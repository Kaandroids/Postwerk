import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { AnalyticsTone, toneVar } from '../../../../../models/analytics.model';
import { donutArcs } from './chart-paths.util';

export interface DonutSegment {
  label: string;
  costCents: number;
  tone: AnalyticsTone;
}

/** Cost-breakdown donut (cents) with a center total and a legend (dot · name · € · %). */
@Component({
  selector: 'app-donut',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="wrap">
      <svg [attr.width]="size" [attr.height]="size" [attr.viewBox]="'0 0 ' + size + ' ' + size"
           class="donut" role="img" [attr.aria-label]="centerLabel() + ' ' + centerValue()">
        <circle [attr.cx]="c" [attr.cy]="c" [attr.r]="r" fill="none" stroke="var(--border)" [attr.stroke-width]="sw" opacity="0.5" />
        @for (a of arcs(); track $index) {
          <circle [attr.cx]="c" [attr.cy]="c" [attr.r]="r" fill="none" class="arc"
                  [attr.stroke]="colorAt($index)" [attr.stroke-width]="sw"
                  [attr.stroke-dasharray]="a.dash + ' ' + (a.circ - a.dash)"
                  [attr.stroke-dashoffset]="-a.off"
                  [attr.transform]="'rotate(-90 ' + c + ' ' + c + ')'" stroke-linecap="butt" />
        }
        <text [attr.x]="c" [attr.y]="c - 2" text-anchor="middle" class="dv">{{ centerValue() }}</text>
        <text [attr.x]="c" [attr.y]="c + 17" text-anchor="middle" class="dl">{{ centerLabel() }}</text>
      </svg>
      <div class="legend">
        @for (s of segments(); track $index) {
          <div class="row">
            <span class="dot" [style.background]="colorAt($index)"></span>
            <span class="name">{{ s.label }}</span>
            <span class="pct">{{ pctOf(s.costCents) }}%</span>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .wrap { display: flex; flex-direction: column; align-items: center; gap: 16px; }
    .donut { flex-shrink: 0; }
    .arc { transition: opacity 0.15s; }
    .dv { font-family: var(--font-serif); font-size: 24px; fill: var(--fg); font-variant-numeric: tabular-nums; }
    .dl { font-family: var(--font-mono); font-size: 9.5px; letter-spacing: 0.06em; fill: var(--fg-subtle); }
    .legend { display: flex; flex-direction: column; gap: 9px; width: 100%; }
    .row { display: flex; align-items: center; gap: 9px; font-size: 12.5px; }
    .dot { width: 9px; height: 9px; border-radius: 3px; flex-shrink: 0; }
    .name { color: var(--fg-muted); flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-family: var(--font-mono); font-size: 11.5px; }
    .pct { font-variant-numeric: tabular-nums; color: var(--fg); font-weight: 600; width: 44px; text-align: right; }
  `],
})
export class DonutComponent {
  segments = input<DonutSegment[]>([]);
  centerValue = input('');
  centerLabel = input('');

  protected readonly size = 168;
  protected readonly r = 64;
  protected readonly sw = 22;
  protected readonly c = 84;

  protected arcs = computed(() => donutArcs(this.segments().map((s) => s.costCents), this.r));

  protected colorAt(i: number): string {
    return toneVar(this.segments()[i].tone);
  }

  protected pctOf(cents: number): number {
    const total = this.segments().reduce((a, b) => a + b.costCents, 0) || 1;
    return Math.round((cents / total) * 100);
  }
}
