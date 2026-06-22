import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { AnalyticsTone, toneVar } from '../../../../../models/analytics.model';

/** CSS horizontal bar — used for success-rate, failure, and node-failure rows. */
@Component({
  selector: 'app-bar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="track"><div class="fill" [style.width.%]="width()" [style.background]="color()"></div></div>`,
  styles: [`
    :host { display: block; flex: 1; min-width: 40px; }
    .track { height: 6px; border-radius: 999px; background: var(--bg-2); overflow: hidden; }
    .fill { height: 100%; border-radius: 999px; transition: width 0.5s ease; }
  `],
})
export class BarComponent {
  pct = input(0);
  tone = input<AnalyticsTone>('success');

  protected width = computed(() => Math.max(2, Math.min(100, this.pct())));
  protected color = computed(() => toneVar(this.tone()));
}
