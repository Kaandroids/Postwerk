import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ParameterSetService } from '../../../../core/services/parameter-set.service';import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { PageContentComponent } from '../page-content/page-content.component';
import { ParameterItem, ParameterSet, ParameterSetRequest, ParameterType, ScalarParameterType, RESERVED_PARAM_NAMES } from '../../../../models/parameter-set.model';
import { CrudPageBase } from '../../../../shared/utils/crud-page.base';
import { humanizeError } from '../../../../shared/utils/error.util';

/** CRUD page for template parameter sets with nested object/list types and import/export. */
@Component({
  selector: 'app-parameter-sets-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, ButtonComponent, ErrorBannerComponent, EmptyStateComponent, PageContentComponent],
  templateUrl: './parameter-sets-page.component.html',
  styleUrl: './parameter-sets-page.component.scss',
})
export class ParameterSetsPageComponent extends CrudPageBase {
  private parameterSetService = inject(ParameterSetService);
  parameterSets = signal<ParameterSet[]>([]);

  // Form fields
  name = signal('');
  parameters = signal<ParameterItem[]>([]);
  parameterTypes: ParameterType[] = ['TEXT', 'NUMBER', 'DATE', 'EMAIL', 'BOOLEAN', 'OBJECT'];
  scalarTypes: ScalarParameterType[] = ['TEXT', 'NUMBER', 'DATE', 'EMAIL', 'BOOLEAN'];
  protected readonly RESERVED_PARAM_NAMES = RESERVED_PARAM_NAMES;

  canSave = computed(() => {
    if (this.name().trim().length < 3 || this.parameters().length === 0) return false;
    return !this.parameters().some(p =>
      RESERVED_PARAM_NAMES.has(p.name) ||
      p.children.some(c => RESERVED_PARAM_NAMES.has(c.name))
    );
  });

  constructor() {
    super();
    this.loadParameterSets();
    this.reloadOnAiMutation('parameterSets', () => this.loadParameterSets());
  }

  toggleLock(ps: ParameterSet): void {
    this.parameterSetService.toggleLock(ps.id).subscribe(updated => this.replaceInList(this.parameterSets, updated));
  }

  editParameterSet(ps: ParameterSet): void {
    this.resetForm();
    this.editId.set(ps.id);
    this.name.set(ps.name);
    this.parameters.set(ps.parameters ? ps.parameters.map(p => ({
      ...p,
      isList: p.isList ?? false,
      required: p.required ?? false,
      children: p.children ? p.children.map(c => ({ ...c, isList: c.isList ?? false, required: c.required ?? false, children: [] })) : [],
    })) : []);
    this.view.set('form');
  }

  deleteParameterSet(id: string): void {
    this.deleteWithConfirm('ps_delete_confirm', () => this.parameterSetService.delete(id), () => this.loadParameterSets());
  }

  addParameter(): void {
    this.parameters.update(list => [...list, {
      name: '', type: 'TEXT', description: '', positiveExample: '', negativeExample: '',
      isList: false, required: false, children: [],
    }]);
  }

  removeParameter(index: number): void {
    this.parameters.update(list => list.filter((_, i) => i !== index));
  }

  updateParameter(index: number, field: keyof ParameterItem, value: string): void {
    this.parameters.update(list => list.map((p, i) => {
      if (i !== index) return p;
      const updated = { ...p, [field]: value };
      if (field === 'type' && value !== 'OBJECT') {
        updated.children = [];
      }
      return updated;
    }));
  }

  toggleIsList(index: number): void {
    this.parameters.update(list => list.map((p, i) =>
      i === index ? { ...p, isList: !p.isList } : p
    ));
  }

  toggleRequired(index: number): void {
    this.parameters.update(list => list.map((p, i) =>
      i === index ? { ...p, required: !p.required } : p
    ));
  }

  toggleChildRequired(parentIndex: number, childIndex: number): void {
    this.parameters.update(list => list.map((p, i) =>
      i === parentIndex ? { ...p, children: p.children.map((c, ci) =>
        ci === childIndex ? { ...c, required: !c.required } : c
      ) } : p
    ));
  }

  addChildParameter(parentIndex: number): void {
    this.parameters.update(list => list.map((p, i) =>
      i === parentIndex ? { ...p, children: [...p.children, {
        name: '', type: 'TEXT' as ParameterType, description: '', positiveExample: '', negativeExample: '',
        isList: false, required: false, children: [],
      }] } : p
    ));
  }

  updateChildParameter(parentIndex: number, childIndex: number, field: keyof ParameterItem, value: string): void {
    this.parameters.update(list => list.map((p, i) =>
      i === parentIndex ? { ...p, children: p.children.map((c, ci) =>
        ci === childIndex ? { ...c, [field]: value } : c
      ) } : p
    ));
  }

  removeChildParameter(parentIndex: number, childIndex: number): void {
    this.parameters.update(list => list.map((p, i) =>
      i === parentIndex ? { ...p, children: p.children.filter((_, ci) => ci !== childIndex) } : p
    ));
  }

  submit(event: Event): void {
    event.preventDefault();
    this.error.set('');
    this.fieldErr.set({});

    const errs: Record<string, boolean> = {};

    if (!this.name() || this.name().length < 3) errs['name'] = true;
    if (this.parameters().length === 0) errs['parameters'] = true;

    for (let i = 0; i < this.parameters().length; i++) {
      const p = this.parameters()[i];
      if (RESERVED_PARAM_NAMES.has(p.name)) errs[`param_${i}_name`] = true;
      for (let ci = 0; ci < p.children.length; ci++) {
        if (RESERVED_PARAM_NAMES.has(p.children[ci].name)) errs[`param_${i}_child_${ci}_name`] = true;
      }
    }

    if (Object.keys(errs).length > 0) {
      this.error.set(this.i18n.t('ps_error_required'));
      this.fieldErr.set(errs);
      return;
    }

    this.loading.set(true);

    const req: ParameterSetRequest = {
      name: this.name(),
      parameters: this.parameters(),
    };

    const op$ = this.editId()
      ? this.parameterSetService.update(this.editId()!, req)
      : this.parameterSetService.create(req);

    op$.subscribe({
      next: () => {
        this.loading.set(false);
        this.editId.set(null);
        this.loadParameterSets();
        this.view.set('list');
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(humanizeError(err, this.i18n.t('ps_error_required')));
      },
    });
  }

  private loadParameterSets(): void {
    this.loadList(this.parameterSetService.list(), this.parameterSets);
  }

  exportData(): void {
    this.exportJson(this.parameterSetService.export(), 'parameter-sets.json');
  }

  importData(event: Event): void {
    this.importJson(event, (d) => this.parameterSetService.import(d), 'ps_import_error', () => this.loadParameterSets());
  }

  protected override resetForm(): void {
    this.name.set('');
    this.parameters.set([]);
    this.error.set('');
    this.fieldErr.set({});
  }
}
