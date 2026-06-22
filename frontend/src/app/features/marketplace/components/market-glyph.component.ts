import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconComponent } from '../../../shared/components/icon/icon.component';

/** Colored rounded glyph for a listing icon. */
@Component({
  selector: 'app-market-glyph',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <span class="mk-glyph" [style.--gc]="color()" [style.width.px]="size()" [style.height.px]="size()"
          [style.borderRadius.px]="radius()">
      <app-icon [name]="icon() || 'cube'" />
    </span>
  `,
})
export class MarketGlyphComponent {
  icon = input<string | null>('cube');
  color = input<string | null>(null);
  size = input<number>(44);
  radius = computed(() => Math.round(this.size() * 0.28));
}
