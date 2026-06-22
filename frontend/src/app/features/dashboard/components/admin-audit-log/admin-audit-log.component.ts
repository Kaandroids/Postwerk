import { ChangeDetectionStrategy, Component, inject, signal, OnInit } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminService } from '../../../../core/services/admin.service';
import { ExportImportService } from '../../../../core/services/export-import.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { PageContentComponent } from '../page-content/page-content.component';
import { AdminAuditLog } from '../../../../models/admin.model';

/** Admin page showing a paginated, filterable audit log of all platform events with CSV export. */
@Component({
  selector: 'app-admin-audit-log',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, PageContentComponent],
  templateUrl: './admin-audit-log.component.html',
  styleUrl: './admin-audit-log.component.scss',
})
export class AdminAuditLogComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  private adminService = inject(AdminService);
  private exportImport = inject(ExportImportService);

  logs = signal<AdminAuditLog[]>([]);
  totalElements = signal(0);
  totalPages = signal(0);
  currentPage = signal(0);
  loading = signal(true);

  actionFilter = signal('');

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.loading.set(true);
    const action = this.actionFilter() || undefined;
    this.adminService.getAuditLog(undefined, action, this.currentPage()).subscribe({
      next: p => {
        this.logs.set(p.content);
        this.totalElements.set(p.totalElements);
        this.totalPages.set(p.totalPages);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onActionFilter(event: Event) {
    this.actionFilter.set((event.target as HTMLSelectElement).value);
    this.currentPage.set(0);
    this.loadData();
  }

  goToPage(page: number) {
    this.currentPage.set(page);
    this.loadData();
  }

  exportCsv() {
    this.adminService.exportAuditLogCsv().subscribe({
      next: resp => {
        if (resp.body) this.exportImport.downloadBlob(resp.body, 'audit-log.csv');
      },
      error: () => { /* export failed — no file downloaded */ },
    });
  }

  /**
   * Localized action label. Falls back to a prettified form (e.g. "Org suspended") for any action
   * without an explicit i18n key, so newly-added backend actions never render as a raw key string.
   */
  actionLabel(action: string): string {
    const key = 'audit_action_' + action;
    const label = this.i18n.t(key);
    return label === key
      ? action.charAt(0) + action.slice(1).toLowerCase().replace(/_/g, ' ')
      : label;
  }

  auditActions = [
    'USER_LOGIN', 'USER_LOGOUT', 'USER_REGISTERED', 'USER_ACCOUNT_DELETED',
    'USER_PROFILE_UPDATED', 'USER_PASSWORD_CHANGED', 'EMAIL_SYNCED',
    'AUTOMATION_CREATED', 'AUTOMATION_EXECUTED', 'AI_CHAT',
    'FILTER_CREATED', 'CATEGORY_CREATED', 'TEMPLATE_CREATED',
  ];
}
