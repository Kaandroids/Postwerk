import { ChangeDetectionStrategy, Component, computed, inject, signal, OnInit } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { ChartUtilService } from '../../../../core/services/chart-util.service';
import { AdminService } from '../../../../core/services/admin.service';
import { PageContentComponent } from '../page-content/page-content.component';
import { AiUsageStats, AiUsageByUser, TimelineDataPoint } from '../../../../models/admin.model';

/** Admin page showing AI usage statistics by model, operation, and per-user breakdown with timeline chart. */
@Component({
  selector: 'app-admin-ai-usage',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PageContentComponent],
  templateUrl: './admin-ai-usage.component.html',
  styleUrl: './admin-ai-usage.component.scss',
})
export class AdminAiUsageComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  private chart = inject(ChartUtilService);
  private adminService = inject(AdminService);

  stats = signal<AiUsageStats | null>(null);
  byUser = signal<AiUsageByUser[]>([]);
  timeline = signal<TimelineDataPoint[]>([]);
  loading = signal(true);
  period = signal('daily');

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.adminService.getAiUsageStats().subscribe({
      next: s => { this.stats.set(s); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
    this.adminService.getAiUsageByUser().subscribe({
      next: u => this.byUser.set(u),
      error: () => this.loading.set(false),
    });
    this.loadTimeline();
  }

  loadTimeline() {
    this.adminService.getAiUsageTimeline(this.period()).subscribe({
      next: t => this.timeline.set(t),
      error: () => { /* silently ignore — timeline remains empty */ },
    });
  }

  onPeriodChange(event: Event) {
    this.period.set((event.target as HTMLSelectElement).value);
    this.loadTimeline();
  }

  chartPath(): string {
    return this.chart.svgLinePath(this.timeline());
  }

  chartAreaPath(): string {
    return this.chart.svgAreaPath(this.timeline());
  }

  /** Largest token bar value, memoized so it isn't recomputed for every row during change detection. */
  private maxBar = computed(() => {
    const s = this.stats();
    if (!s) return 1;
    return Math.max(...s.byModel.map(m => m.totalTokens), ...s.byOperation.map(o => o.totalTokens), 1);
  });

  barWidth(value: number): number {
    return Math.max((value / this.maxBar()) * 100, 1);
  }

  formatCost(cents: number): string {
    return this.fmt.eurFromCents(cents);
  }
}
