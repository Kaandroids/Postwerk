import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { IconComponent } from '../../../shared/components/icon/icon.component';
import { I18nService } from '../../../core/services/i18n.service';
import { MarketGlyphComponent } from './market-glyph.component';
import { MarketStarsComponent } from './market-stars.component';
import { MarketplaceListing, visibilityKey, visibilityLabelKey } from '../../../models/marketplace.model';
import { pricingLabel } from '../../../core/services/marketplace.service';

/** Compact marketplace listing card. */
@Component({
  selector: 'app-market-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, MarketGlyphComponent, MarketStarsComponent],
  template: `
    <button class="mk-card" type="button" [attr.data-testid]="'mk-card-' + item().id" (click)="open.emit(item().id)">
      <app-market-glyph [icon]="item().icon" [color]="item().color" [size]="44" />
      <div class="mk-card-body">
        <div class="mk-card-row1">
          <span class="mk-card-name">{{ item().name }}</span>
          @if (item().verified) {
            <span class="vrf"><app-icon name="verified" /></span>
          }
          @if (item().kind === 'INTEGRATION') {
            <span class="mk-kind" data-k="integration">{{ i18n.t('mkt_kind_integration') }}</span>
          }
          <span class="mk-card-top">
            <span class="mk-vis" [attr.data-v]="visKey()">{{ visLabel() }}</span>
          </span>
        </div>
        <span class="mk-card-tag">{{ item().tagline }}</span>
        <div class="mk-card-foot">
          <app-market-stars [rating]="item().ratingAvg" [showNum]="true" />
          <span class="mk-metric"><app-icon name="download" />{{ item().installCount }}</span>
          <span class="sp"></span>
          @if (item().owned) {
            <span class="mk-badge verified"><app-icon name="checkCircle" /> {{ i18n.t('mkt_owned') }}</span>
          } @else {
            <span class="mk-price" [attr.data-free]="item().pricingModel === 'FREE' ? '1' : null">{{ price() }}</span>
          }
        </div>
      </div>
    </button>
  `,
})
export class MarketCardComponent {
  protected i18n = inject(I18nService);
  item = input.required<MarketplaceListing>();
  open = output<string>();

  price = computed(() => pricingLabel(this.item().pricingModel, this.item().price, (k) => this.i18n.t(k)));
  visKey = computed(() => visibilityKey(this.item().visibility));
  visLabel = computed(() => this.i18n.t(visibilityLabelKey(this.item().visibility)));
}
