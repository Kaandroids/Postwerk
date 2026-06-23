import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AutomationService } from '../../../../core/services/automation.service';import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { ButtonComponent } from '../../../../shared/components/button/button.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { ColorPickerComponent } from '../../../../shared/components/color-picker/color-picker.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { ManualRunDialogComponent } from '../../../../shared/components/manual-run-dialog/manual-run-dialog.component';
import { PageContentComponent } from '../page-content/page-content.component';
import { DatePipe } from '@angular/common';
import { Automation, AutomationRequest, AutomationStatus, DEFAULT_COLORS } from '../../../../models/automation.model';
import { CrudPageBase } from '../../../../shared/utils/crud-page.base';
import { humanizeError } from '../../../../shared/utils/error.util';

/** CRUD list page for email automations with status toggles, lock controls, and import/export. */
@Component({
  selector: 'app-automations-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, ButtonComponent, ErrorBannerComponent, ColorPickerComponent, EmptyStateComponent, ManualRunDialogComponent, PageContentComponent, DatePipe],
  templateUrl: './automations-page.component.html',
  styleUrl: './automations-page.component.scss',
})
export class AutomationsPageComponent extends CrudPageBase {
  private automationService = inject(AutomationService);  private router = inject(Router);

  readonly colors = DEFAULT_COLORS;

  automations = signal<Automation[]>([]);

  // Form fields
  name = signal('');
  description = signal('');
  color = signal(DEFAULT_COLORS[0]);


  activeCount = computed(() => this.automations().filter(a => a.status === 'ACTIVE').length);
  totalNodes = computed(() => this.automations().reduce((s, a) => s + a.nodeCount, 0));

  // ── List filtering ──────────────────────────────────────────
  statusFilter = signal<'ALL' | AutomationStatus>('ALL');
  searchQuery = signal('');

  testingCount = computed(() => this.automations().filter(a => a.status === 'TESTING').length);
  pausedCount = computed(() => this.automations().filter(a => a.status === 'PAUSED').length);

  // On phones the segmented status tabs collapse into a single button that opens
  // a bottom sheet (the "Simulationsmodus" label is too wide for a phone row).
  filterSheetOpen = signal(false);
  filterOptions = computed<{ value: 'ALL' | AutomationStatus; labelKey: string; count: number }[]>(() => [
    { value: 'ALL', labelKey: 'auto_filter_all', count: this.automations().length },
    { value: 'ACTIVE', labelKey: 'auto_status_active', count: this.activeCount() },
    { value: 'TESTING', labelKey: 'auto_status_testing', count: this.testingCount() },
    { value: 'PAUSED', labelKey: 'auto_status_paused', count: this.pausedCount() },
  ]);
  activeFilterLabelKey = computed(() =>
    this.filterOptions().find(o => o.value === this.statusFilter())?.labelKey ?? 'auto_filter_all');

  pickFilter(value: 'ALL' | AutomationStatus): void {
    this.statusFilter.set(value);
    this.filterSheetOpen.set(false);
  }

  filteredAutomations = computed(() => {
    const status = this.statusFilter();
    const q = this.searchQuery().trim().toLowerCase();
    return this.automations().filter(a => {
      if (status !== 'ALL' && a.status !== status) return false;
      if (q && !(a.name.toLowerCase().includes(q) || (a.description || '').toLowerCase().includes(q))) return false;
      return true;
    });
  });

  getSuccessRate(a: Automation): number {
    if (a.totalExecutions === 0) return 0;
    return Math.round((a.successCount / a.totalExecutions) * 100);
  }

  constructor() {
    super();
    this.loadAutomations();
    this.reloadOnAiMutation('automations', () => this.loadAutomations());
  }

  editAutomation(a: Automation): void {
    this.resetForm();
    this.editId.set(a.id);
    this.name.set(a.name);
    this.description.set(a.description || '');
    this.color.set(a.color);
    this.view.set('form');
  }

  openEditor(a: Automation): void {
    this.router.navigate(['/dashboard/automations', a.id, 'edit']);
  }

  deleteAutomation(id: string): void {
    this.deleteWithConfirm('auto_delete_confirm', () => this.automationService.delete(id), () => this.loadAutomations());
  }

  toggleLock(a: Automation): void {
    this.automationService.toggleLock(a.id).subscribe(updated => this.replaceInList(this.automations, updated));
  }

  toggleStatus(a: Automation): void {
    const newStatus = a.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE';
    this.automationService.updateStatus(a.id, newStatus).subscribe({
      next: () => this.loadAutomations(),
      error: (err) => this.error.set(humanizeError(err, 'Error')),
    });
  }

  setStatus(a: Automation, status: AutomationStatus): void {
    if (a.status === status) return;
    this.automationService.updateStatus(a.id, status).subscribe({
      next: () => this.loadAutomations(),
      error: (err) => this.error.set(humanizeError(err, 'Error')),
    });
  }

  statusMenuOpen = signal<string | null>(null);

  toggleStatusMenu(a: Automation): void {
    this.statusMenuOpen.set(this.statusMenuOpen() === a.id ? null : a.id);
  }

  /** The automation whose manual-run dialog is open (null = closed). */
  manualRunFor = signal<Automation | null>(null);

  runNow(a: Automation): void {
    this.manualRunFor.set(a);
  }


  submit(event: Event): void {
    event.preventDefault();
    this.error.set('');
    this.fieldErr.set({});

    const errs: Record<string, boolean> = {};
    if (!this.name().trim()) errs['name'] = true;

    if (Object.keys(errs).length > 0) {
      this.error.set(this.i18n.t('auto_error_required'));
      this.fieldErr.set(errs);
      return;
    }

    this.loading.set(true);

    const req: AutomationRequest = {
      name: this.name(),
      description: this.description() || null,
      color: this.color(),
    };

    const op$ = this.editId()
      ? this.automationService.update(this.editId()!, req)
      : this.automationService.create(req);

    op$.subscribe({
      next: () => {
        this.loading.set(false);
        this.editId.set(null);
        this.loadAutomations();
        this.view.set('list');
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(humanizeError(err, 'Error'));
      },
    });
  }

  private loadAutomations(): void {
    this.loadList(this.automationService.list(), this.automations);
  }

  exportData(): void {
    this.exportJson(this.automationService.export(), 'automations.json');
  }

  importData(event: Event): void {
    this.importJson(event, (d) => this.automationService.import(d), 'auto_import_error', () => this.loadAutomations());
  }

  protected override resetForm(): void {
    this.name.set('');
    this.description.set('');
    this.color.set(DEFAULT_COLORS[Math.floor(Math.random() * DEFAULT_COLORS.length)]);
    this.error.set('');
    this.fieldErr.set({});
  }
}
