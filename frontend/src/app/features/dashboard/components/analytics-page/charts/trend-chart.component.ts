import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { I18nService } from '../../../../../core/services/i18n.service';
import { TrendPoint } from '../../../../../models/analytics.model';
import { nextGradId } from './chart-paths.util';

/** Dual-series (success/failed) execution-trend chart with a hover crosshair + tooltip. */
@Component({
  selector: 'app-trend-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  template: `
    <div class="chart" [attr.data-testid]="testid()">
      <div class="plot" [style.height.px]="height()" role="img" [attr.aria-label]="ariaLabel()"
           (mousemove)="onMove($event)" (mouseleave)="hover.set(null)">
        <svg width="100%" [attr.height]="height()" viewBox="0 0 600 120" preserveAspectRatio="none" class="svg" aria-hidden="true">
          <defs>
            <linearGradient [attr.id]="gidS" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="var(--success)" stop-opacity="0.14" />
              <stop offset="100%" stop-color="var(--success)" stop-opacity="0" />
            </linearGradient>
            <linearGradient [attr.id]="gidF" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="var(--danger)" stop-opacity="0.14" />
              <stop offset="100%" stop-color="var(--danger)" stop-opacity="0" />
            </linearGradient>
          </defs>
          <path [attr.d]="totalLine()" fill="none" stroke="var(--border-strong)" stroke-width="1"
                vector-effect="non-scaling-stroke" stroke-dasharray="3 3" opacity="0.7" />
          <path [attr.d]="successPath().area" [attr.fill]="'url(#' + gidS + ')'" />
          <path [attr.d]="successPath().line" fill="none" stroke="var(--success)" stroke-width="1.5"
                vector-effect="non-scaling-stroke" stroke-linecap="round" stroke-linejoin="round" />
          <path [attr.d]="failedPath().area" [attr.fill]="'url(#' + gidF + ')'" />
          <path [attr.d]="failedPath().line" fill="none" stroke="var(--danger)" stroke-width="1.5"
                vector-effect="non-scaling-stroke" stroke-linecap="round" stroke-linejoin="round" />
          @if (hover() !== null) {
            <line [attr.x1]="xAt(hover()!)" [attr.x2]="xAt(hover()!)" [attr.y1]="padTop" [attr.y2]="padTop + ih"
                  stroke="var(--fg-subtle)" stroke-width="1" vector-effect="non-scaling-stroke" opacity="0.5" />
          }
        </svg>

        @if (hover() !== null) {
          <span class="dot s" [style.left.%]="leftPct(hover()!)" [style.top.%]="topPct(point(hover()!).success)"></span>
          <span class="dot f" [style.left.%]="leftPct(hover()!)" [style.top.%]="topPct(point(hover()!).failed)"></span>
          <div class="tip" [style.left.%]="leftPct(hover()!)">
            <div class="tip-date">{{ dayLabel(point(hover()!).date) }}</div>
            <div class="tip-row"><span class="tip-k">{{ i18n.t('an_tip_total') }}</span><span class="tip-v">{{ point(hover()!).total | number }}</span></div>
            <div class="tip-row"><span class="tip-dot" style="background: var(--success)"></span>{{ i18n.t('an_tip_success') }}<span class="tip-v">{{ point(hover()!).success | number }}</span></div>
            <div class="tip-row"><span class="tip-dot" style="background: var(--danger)"></span>{{ i18n.t('an_tip_failed') }}<span class="tip-v">{{ point(hover()!).failed | number }}</span></div>
          </div>
        }
      </div>

      <div class="xaxis">
        @for (ti of ticks(); track $index) {
          <span class="tick">{{ dayLabel(data()[ti].date) }}</span>
        }
      </div>

      <table class="sr-only">
        <caption>{{ i18n.t('an_exec_trend') }}</caption>
        <thead><tr><th>{{ i18n.t('an_col_started') }}</th><th>{{ i18n.t('an_tip_total') }}</th><th>{{ i18n.t('an_tip_success') }}</th><th>{{ i18n.t('an_tip_failed') }}</th></tr></thead>
        <tbody>
          @for (p of data(); track $index) {
            <tr><td>{{ dayLabel(p.date) }}</td><td>{{ p.total }}</td><td>{{ p.success }}</td><td>{{ p.failed }}</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: [`
    .plot { position: relative; width: 100%; }
    .svg { display: block; width: 100%; height: 100%; overflow: visible; }
    .xaxis { display: flex; justify-content: space-between; margin-top: 8px; }
    .tick { font-family: var(--font-mono); font-size: 10px; color: var(--fg-subtle); }
    .dot { position: absolute; width: 7px; height: 7px; border-radius: 50%; transform: translate(-50%, -50%); border: 1.5px solid var(--bg); pointer-events: none; }
    .dot.s { background: var(--success); }
    .dot.f { background: var(--danger); }
    .tip { position: absolute; top: -6px; transform: translate(-50%, -100%); background: var(--bg-panel); color: var(--bg-panel-fg, #fff);
           border-radius: 9px; padding: 9px 11px; font-size: 11.5px; min-width: 124px;
           box-shadow: 0 8px 28px -10px rgba(0,0,0,0.4); pointer-events: none; z-index: 5; }
    .tip-date { font-family: var(--font-mono); font-size: 10.5px; opacity: 0.7; margin-bottom: 6px; }
    .tip-row { display: flex; align-items: center; gap: 6px; padding: 1px 0; }
    .tip-k { opacity: 0.75; }
    .tip-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
    .tip-v { margin-left: auto; font-variant-numeric: tabular-nums; font-weight: 600; }
    .sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
  `],
})
export class TrendChartComponent {
  protected readonly i18n = inject(I18nService);

  data = input<TrendPoint[]>([]);
  height = input(180);
  testid = input<string>('');

  protected readonly hover = signal<number | null>(null);
  protected readonly gidS = nextGradId('an-trend-s');
  protected readonly gidF = nextGradId('an-trend-f');

  private readonly W = 600;
  private readonly H = 120;
  private readonly padX = 4;
  protected readonly padTop = 8;
  protected readonly ih = this.H - this.padTop - 4;

  private max = computed(() => Math.max(...this.data().map((d) => d.total), 1));

  protected xAt(i: number): number {
    const n = this.data().length;
    return this.padX + (n <= 1 ? 0 : (i / (n - 1)) * (this.W - this.padX * 2));
  }
  protected yAt(v: number): number {
    return this.padTop + (1 - v / this.max()) * this.ih;
  }
  protected leftPct(i: number): number { return (this.xAt(i) / this.W) * 100; }
  protected topPct(v: number): number { return (this.yAt(v) / this.H) * 100; }
  protected point(i: number): TrendPoint { return this.data()[i]; }

  private mk(key: 'success' | 'failed' | 'total') {
    const d = this.data();
    if (!d.length) return { line: '', area: '' };
    const line = d.map((p, i) => `${i ? 'L' : 'M'}${this.xAt(i).toFixed(2)} ${this.yAt(p[key]).toFixed(2)}`).join(' ');
    const baseY = this.padTop + this.ih;
    const area = `${line} L${this.xAt(d.length - 1).toFixed(2)} ${baseY} L${this.xAt(0).toFixed(2)} ${baseY} Z`;
    return { line, area };
  }
  protected successPath = computed(() => this.mk('success'));
  protected failedPath = computed(() => this.mk('failed'));
  protected totalLine = computed(() => this.mk('total').line);

  protected ticks = computed(() => {
    const n = this.data().length;
    const tc = Math.min(6, n);
    if (tc <= 1) return n ? [0] : [];
    return Array.from({ length: tc }, (_, i) => Math.round((i / (tc - 1)) * (n - 1)));
  });

  protected ariaLabel = computed(() => {
    const total = this.data().reduce((a, b) => a + b.total, 0);
    const failed = this.data().reduce((a, b) => a + b.failed, 0);
    return `${this.i18n.t('an_exec_trend')}: ${total} / ${total - failed} / ${failed}`;
  });

  protected onMove(e: MouseEvent): void {
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    if (!rect.width) return;
    const n = this.data().length;
    if (!n) return;
    const rel = (e.clientX - rect.left) / rect.width;
    this.hover.set(Math.max(0, Math.min(n - 1, Math.round(rel * (n - 1)))));
  }

  protected dayLabel(iso: string): string {
    const locale = this.i18n.lang() === 'de' ? 'de-DE' : 'en-US';
    return new Date(iso).toLocaleDateString(locale, { day: '2-digit', month: 'short' });
  }
}
