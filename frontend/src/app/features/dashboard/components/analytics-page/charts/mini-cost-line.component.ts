import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { areaPath, linePath, nextGradId } from './chart-paths.util';

/** Thin daily-cost line shown under the AI-cost donut. Warning-toned area + line. */
@Component({
  selector: 'app-mini-cost-line',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg width="100%" height="36" viewBox="0 0 600 36" preserveAspectRatio="none" aria-hidden="true">
      <defs>
        <linearGradient [attr.id]="gid" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="var(--warning)" stop-opacity="0.14" />
          <stop offset="100%" stop-color="var(--warning)" stop-opacity="0" />
        </linearGradient>
      </defs>
      <path [attr.d]="area()" [attr.fill]="'url(#' + gid + ')'" />
      <path [attr.d]="line()" fill="none" stroke="var(--warning)" stroke-width="1.5"
            vector-effect="non-scaling-stroke" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
  `,
  styles: [`:host { display: block; width: 100%; height: 36px; } svg { display: block; }`],
})
export class MiniCostLineComponent {
  data = input<number[]>([]);

  protected readonly gid = nextGradId('an-mini');
  protected line = computed(() => linePath(this.data(), 600, 36, 2));
  protected area = computed(() => areaPath(this.data(), 600, 36, 2));
}
