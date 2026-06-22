import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { I18nService } from '../../../../core/services/i18n.service';
import { PendingActionService } from '../../../../core/services/pending-action.service';
import { CategoryService } from '../../../../core/services/category.service';
import { PendingAction } from '../../../../models/pending-action.model';
import { Category } from '../../../../models/category.model';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { PageContentComponent } from '../page-content/page-content.component';
import { humanizeError } from '../../../../shared/utils/error.util';

/** Approval inbox for supervised-mode actions (#3a): review & approve/reject parked side effects. */
@Component({
  selector: 'app-approvals-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ErrorBannerComponent, EmptyStateComponent, PageContentComponent, DatePipe],
  templateUrl: './approvals-page.component.html',
  styleUrl: './approvals-page.component.scss',
})
export class ApprovalsPageComponent implements OnInit {
  protected i18n = inject(I18nService);
  private service = inject(PendingActionService);
  private categoryService = inject(CategoryService);

  actions = signal<PendingAction[]>([]);
  categories = signal<Category[]>([]);
  loading = signal(true);
  error = signal('');
  busyId = signal<string | null>(null);
  correctingId = signal<string | null>(null);

  ngOnInit() {
    this.load();
    this.categoryService.list().subscribe({
      next: cats => this.categories.set(cats),
      error: () => { /* non-critical — correction UI just won't be available */ },
    });
  }

  toggleCorrect(a: PendingAction) {
    this.correctingId.set(this.correctingId() === a.id ? null : a.id);
  }

  reclassify(a: PendingAction, categoryId: string) {
    if (!categoryId) return;
    this.busyId.set(a.id);
    this.correctingId.set(null);
    this.error.set('');
    this.service.reclassify(a.id, categoryId).subscribe({
      next: () => { this.busyId.set(null); this.remove(a.id); },
      error: (err) => { this.busyId.set(null); this.error.set(humanizeError(err, this.i18n.t('approvals_action_failed'))); },
    });
  }

  load() {
    this.loading.set(true);
    this.error.set('');
    this.service.list('PENDING').subscribe({
      next: page => { this.actions.set(page.content); this.loading.set(false); },
      error: (err) => { this.error.set(humanizeError(err, this.i18n.t('approvals_load_failed'))); this.loading.set(false); },
    });
  }

  approve(a: PendingAction) {
    this.busyId.set(a.id);
    this.error.set('');
    this.service.approve(a.id).subscribe({
      next: () => { this.busyId.set(null); this.remove(a.id); },
      error: (err) => { this.busyId.set(null); this.error.set(humanizeError(err, this.i18n.t('approvals_action_failed'))); },
    });
  }

  reject(a: PendingAction) {
    this.busyId.set(a.id);
    this.error.set('');
    this.service.reject(a.id).subscribe({
      next: () => { this.busyId.set(null); this.remove(a.id); },
      error: (err) => { this.busyId.set(null); this.error.set(humanizeError(err, this.i18n.t('approvals_action_failed'))); },
    });
  }

  private remove(id: string) {
    this.actions.update(list => list.filter(x => x.id !== id));
  }

  /** Resolved payload as readable label/value rows (HTML stripped from body fields for display). */
  detailEntries(a: PendingAction): { key: string; value: string }[] {
    return Object.entries(a.actionDetail || {})
      .filter(([k]) => k !== 'reason' && k !== 'mocked')
      .map(([key, value]) => ({ key, value: this.render(value) }));
  }

  private render(v: unknown): string {
    if (v == null) return '';
    const s = typeof v === 'string' ? v : JSON.stringify(v);
    return s.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
  }
}
