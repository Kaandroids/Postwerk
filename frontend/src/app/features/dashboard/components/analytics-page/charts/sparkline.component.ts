import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { AnalyticsTone, toneVar } from '../../../../../models/analytics.model';
import { areaPath, linePath, nextGradId } from './chart-paths.util';

/** Tiny KPI-card sparkline — area + line over a fixed 56×h viewBox, stretched to fill. */
@Component({
  selector: 'app-sparkline',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg width="100%" [attr.height]="h()" [attr.viewBox]="'0 0 56 ' + h()" preserveAspectRatio="none" aria-hidden="true">
      <defs>
        <linearGradient [attr.id]="gid" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" [attr.stop-color]="color()" stop-opacity="0.20" />
          <stop offset="100%" [attr.stop-color]="color()" stop-opacity="0" />
        </linearGradient>
      </defs>
      <path [attr.d]="area()" [attr.fill]="'url(#' + gid + ')'" />
      <path [attr.d]="line()" fill="none" [attr.stroke]="color()" stroke-width="1.5"
            vector-effect="non-scaling-stroke" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
  `,
  styles: [`:host { display: block; width: 56px; } svg { display: block; }`],
})
export class SparklineComponent {
  data = input<number[]>([]);
  tone = input<AnalyticsTone>('accent');
  h = input(28);

  protected readonly gid = nextGradId('an-spark');
  private readonly W = 56;
  private readonly pad = 2;

  protected color = computed(() => toneVar(this.tone()));
  protected line = computed(() => linePath(this.data(), this.W, this.h(), this.pad));
  protected area = computed(() => areaPath(this.data(), this.W, this.h(), this.pad));
}
