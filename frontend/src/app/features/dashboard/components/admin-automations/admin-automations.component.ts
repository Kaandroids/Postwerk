import { ChangeDetectionStrategy, Component, inject, signal, OnInit } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminService } from '../../../../core/services/admin.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { PageContentComponent } from '../page-content/page-content.component';
import { AutomationStats, AutomationExecution } from '../../../../models/admin.model';

/** Admin page displaying automation statistics and paginated execution history. */
@Component({
  selector: 'app-admin-automations',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, PageContentComponent, DecimalPipe],
  templateUrl: './admin-automations.component.html',
  styleUrl: './admin-automations.component.scss',
})
export class AdminAutomationsComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  private adminService = inject(AdminService);

  stats = signal<AutomationStats | null>(null);
  executions = signal<AutomationExecution[]>([]);
  totalPages = signal(0);
  currentPage = signal(0);
  loading = signal(true);

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.adminService.getAutomationStats().subscribe({
      next: s => { this.stats.set(s); this.loading.set(false); },
      // Without this, a failed stats load leaves the page spinning forever.
      error: () => this.loading.set(false),
    });
    this.loadExecutions();
  }

  loadExecutions() {
    this.adminService.getAutomationExecutions(this.currentPage()).subscribe({
      next: p => {
        this.executions.set(p.content);
        this.totalPages.set(p.totalPages);
      },
      error: () => { /* executions remain empty — the empty state renders */ },
    });
  }

  goToPage(page: number) {
    this.currentPage.set(page);
    this.loadExecutions();
  }

  successRateWidth(): number {
    const s = this.stats();
    if (!s || s.totalExecutions === 0) return 0;
    return s.successRate;
  }

  /** Maps an execution status to a shared badge tone (.adm-badge data-tone). */
  execTone(status: string): string {
    switch (status) {
      case 'SUCCESS': return 'green';
      case 'FAILED': return 'danger';
      case 'RUNNING': return 'amber';
      default: return 'slate';
    }
  }
}
