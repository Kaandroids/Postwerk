import { ChangeDetectionStrategy, Component, computed, inject, signal, ViewChild } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import DOMPurify from 'dompurify';
import { TemplateService } from '../../../../core/services/template.service';import { ParameterSetService } from '../../../../core/services/parameter-set.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { PageContentComponent } from '../page-content/page-content.component';
import { TiptapEditorComponent } from '../../../../shared/components/tiptap-editor/tiptap-editor.component';
import { Template, TemplateRequest } from '../../../../models/template.model';
import { CrudPageBase } from '../../../../shared/utils/crud-page.base';
import { humanizeError } from '../../../../shared/utils/error.util';
import { ParameterSet } from '../../../../models/parameter-set.model';

/** CRUD page for email templates with visual/HTML editor, parameter set linking, and import/export. */
@Component({
  selector: 'app-templates-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, ButtonComponent, ErrorBannerComponent, EmptyStateComponent, PageContentComponent, TiptapEditorComponent, SlicePipe],
  templateUrl: './templates-page.component.html',
  styleUrl: './templates-page.component.scss',
})
export class TemplatesPageComponent extends CrudPageBase {
  private templateService = inject(TemplateService);  private sanitizer = inject(DomSanitizer);
  private parameterSetService = inject(ParameterSetService);

  @ViewChild(TiptapEditorComponent) tiptapEditor?: TiptapEditorComponent;

  templates = signal<Template[]>([]);

  // Form fields
  name = signal('');
  subject = signal('');
  body = signal('');

  // Editor mode
  editorMode = signal<'visual' | 'html'>('visual');

  // Parameter set
  useParameterSet = signal(false);
  parameterSetId = signal<string | null>(null);
  parameterSets = signal<ParameterSet[]>([]);

  detectedParams = computed(() => {
    const pattern = /\{\{(\w+)}}/g;
    const params: string[] = [];
    const combined = this.subject() + ' ' + this.body();
    let match: RegExpExecArray | null;
    while ((match = pattern.exec(combined)) !== null) {
      if (!params.includes(match[1])) {
        params.push(match[1]);
      }
    }
    return params;
  });

  selectedParameterSet = computed(() => {
    const id = this.parameterSetId();
    if (!id) return null;
    return this.parameterSets().find(ps => ps.id === id) ?? null;
  });

  sanitizedBody = computed<SafeHtml>(() => {
    const raw = this.body();
    if (!raw) return this.i18n.t('tpl_preview_empty_body');
    const clean = DOMPurify.sanitize(raw);
    return this.sanitizer.bypassSecurityTrustHtml(clean);
  });

  canSave = computed(() =>
    this.name().trim().length > 0 &&
    this.subject().trim().length > 0 &&
    this.body().trim().length > 0
  );

  constructor() {
    super();
    this.loadTemplates();
    this.loadParameterSets();
    this.reloadOnAiMutation('templates', () => this.loadTemplates());
    this.reloadOnAiMutation('parameterSets', () => this.loadParameterSets());
  }

  toggleLock(t: Template): void {
    this.templateService.toggleLock(t.id).subscribe(updated => this.replaceInList(this.templates, updated));
  }

  editTemplate(t: Template): void {
    this.resetForm();
    this.editId.set(t.id);
    this.name.set(t.name);
    this.subject.set(t.subject);
    this.body.set(t.body);
    if (t.parameterSetId) {
      this.useParameterSet.set(true);
      this.parameterSetId.set(t.parameterSetId);
    }
    this.view.set('form');
  }

  deleteTemplate(id: string): void {
    this.deleteWithConfirm('tpl_delete_confirm', () => this.templateService.delete(id), () => this.loadTemplates());
  }

  switchEditorMode(mode: 'visual' | 'html'): void {
    if (mode === this.editorMode()) return;

    if (mode === 'visual') {
      this.editorMode.set(mode);
      setTimeout(() => {
        this.tiptapEditor?.setContent(this.body());
      }, 0);
    } else {
      this.editorMode.set(mode);
    }
  }

  onTiptapChange(html: string): void {
    this.body.set(html);
  }

  onUseParameterSetChange(e: Event): void {
    const checked = (e.target as HTMLInputElement).checked;
    this.useParameterSet.set(checked);
    if (!checked) {
      this.parameterSetId.set(null);
    }
  }

  submit(event: Event): void {
    event.preventDefault();
    this.error.set('');
    this.fieldErr.set({});

    const errs: Record<string, boolean> = {};
    if (!this.name().trim()) errs['name'] = true;
    if (!this.subject().trim()) errs['subject'] = true;
    if (!this.body().trim()) errs['body'] = true;

    if (Object.keys(errs).length > 0) {
      this.error.set(this.i18n.t('tpl_error_required'));
      this.fieldErr.set(errs);
      return;
    }

    this.loading.set(true);

    const req: TemplateRequest = {
      name: this.name(),
      subject: this.subject(),
      body: this.body(),
      parameterSetId: this.useParameterSet() ? this.parameterSetId() : null,
    };

    const op$ = this.editId()
      ? this.templateService.update(this.editId()!, req)
      : this.templateService.create(req);

    op$.subscribe({
      next: () => {
        this.loading.set(false);
        this.editId.set(null);
        this.loadTemplates();
        this.view.set('list');
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(humanizeError(err, 'Error'));
      },
    });
  }

  private loadTemplates(): void {
    this.loadList(this.templateService.list(), this.templates);
  }

  private loadParameterSets(): void {
    this.loadList(this.parameterSetService.list(), this.parameterSets);
  }

  exportData(): void {
    this.exportJson(this.templateService.export(), 'templates.json');
  }

  importData(event: Event): void {
    this.importJson(event, (d) => this.templateService.import(d), 'tpl_import_error', () => this.loadTemplates());
  }

  protected override resetForm(): void {
    this.name.set('');
    this.subject.set('');
    this.body.set('');
    this.editorMode.set('visual');
    this.useParameterSet.set(false);
    this.parameterSetId.set(null);
    this.error.set('');
    this.fieldErr.set({});
  }
}
