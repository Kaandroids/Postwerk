import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconComponent } from '../../../shared/components/icon/icon.component';

/** Five-star rating display with optional numeric value. */
@Component({
  selector: 'app-market-stars',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <span class="mk-stars">
      @for (i of stars; track i) {
        <app-icon [name]="i <= full() ? 'starFilled' : 'star'" [class.dim]="i > full()" />
      }
      @if (showNum()) {
        <span class="mk-rate-num">{{ rating().toFixed(1) }}</span>
      }
    </span>
  `,
})
export class MarketStarsComponent {
  rating = input<number>(0);
  showNum = input<boolean>(false);
  readonly stars = [1, 2, 3, 4, 5];
  full = computed(() => Math.round(this.rating()));
}
