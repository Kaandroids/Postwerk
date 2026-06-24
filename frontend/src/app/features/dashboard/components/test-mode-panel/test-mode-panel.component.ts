import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { AutomationService } from '../../../../core/services/automation.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { TestModeResult, TestModeStats, getNodeColor, getNodeIcon } from '../../../../models/automation.model';
import { DatePipe } from '@angular/common';

type FeedbackFilter = 'ALL' | 'PENDING' | 'CORRECT' | 'INCORRECT';

@Component({
  selector: 'app-test-mode-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, DatePipe],
  templateUrl: './test-mode-panel.component.html',
  styleUrl: './test-mode-panel.component.scss',
})
export class TestModePanelComponent implements OnInit, OnDestroy {
  protected i18n = inject(I18nService);
  private automationService = inject(AutomationService);

  automationId = input.required<string>();

  /** Live-feed refresh: simulations are recorded asynchronously when real emails are processed. */
  private static readonly POLL_INTERVAL_MS = 8000;
  private pollTimer: ReturnType<typeof setInterval> | null = null;

  stats = signal<TestModeStats | null>(null);
  results = signal<TestModeResult[]>([]);
  totalElements = signal(0);
  page = signal(0);
  filter = signal<FeedbackFilter>('ALL');
  loading = signal(false);

  filters: FeedbackFilter[] = ['ALL', 'PENDING', 'CORRECT', 'INCORRECT'];

  accuracyLabel = computed(() => {
    const s = this.stats();
    if (!s || (s.correct + s.incorrect) === 0) return '–';
    return `${s.accuracyPercent}%`;
  });

  /** Total simulated actions across the currently loaded feed page. */
  simulatedCount = computed(() => this.results().reduce((sum, r) => sum + r.simulatedActions.length, 0));

  readonly getNodeColor = getNodeColor;
  readonly getNodeIcon = getNodeIcon;

  /** Grade keyword for a feed card's left accent border. */
  grade(r: TestModeResult): 'correct' | 'wrong' | 'pending' {
    if (r.feedback === 'CORRECT') return 'correct';
    if (r.feedback === 'INCORRECT') return 'wrong';
    return 'pending';
  }

  /** Two-letter avatar initials derived from a sender address. */
  initials(from: string | null): string {
    if (!from) return '··';
    return from.replace(/@.*$/, '').slice(0, 2).toUpperCase();
  }

  ngOnInit(): void {
    this.loadStats();
    this.loadResults();
    // A TESTING automation records a simulation entry whenever a real email is processed (the poller
    // runs ~every 60s). Refresh silently so new entries appear without leaving and re-opening the panel.
    this.pollTimer = setInterval(() => {
      this.loadStats();
      this.loadResults(true);
    }, TestModePanelComponent.POLL_INTERVAL_MS);
  }

  ngOnDestroy(): void {
    if (this.pollTimer) clearInterval(this.pollTimer);
  }

  setFilter(f: FeedbackFilter): void {
    this.filter.set(f);
    this.page.set(0);
    this.loadResults();
  }

  loadStats(): void {
    this.automationService.getTestModeStats(this.automationId()).subscribe({
      next: (s) => this.stats.set(s),
    });
  }

  loadResults(silent = false): void {
    if (!silent) this.loading.set(true);
    const feedback = this.filter() === 'ALL' ? undefined : this.filter();
    this.automationService.getTestModeResults(this.automationId(), feedback, this.page()).subscribe({
      next: (res) => {
        this.results.set(res.content);
        this.totalElements.set(res.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  submitFeedback(result: TestModeResult, feedback: 'CORRECT' | 'INCORRECT'): void {
    this.automationService.submitTestModeFeedback(this.automationId(), result.id, feedback).subscribe({
      next: (updated) => {
        const list = this.results().map(r => r.id === updated.id ? updated : r);
        this.results.set(list);
        this.loadStats();
      },
    });
  }

  clearResults(): void {
    this.automationService.clearTestModeResults(this.automationId()).subscribe({
      next: () => {
        this.results.set([]);
        this.totalElements.set(0);
        this.loadStats();
      },
    });
  }

  /** Deletes a single result so it no longer skews the accuracy statistics. */
  deleteResult(result: TestModeResult): void {
    this.automationService.deleteTestModeResult(this.automationId(), result.id).subscribe({
      next: () => {
        this.results.update(list => list.filter(r => r.id !== result.id));
        this.totalElements.update(n => Math.max(0, n - 1));
        this.loadStats();
      },
    });
  }

  nextPage(): void {
    this.page.update(p => p + 1);
    this.loadResults();
  }

  prevPage(): void {
    this.page.update(p => Math.max(0, p - 1));
    this.loadResults();
  }

  hasNextPage = computed(() => (this.page() + 1) * 20 < this.totalElements());
}
