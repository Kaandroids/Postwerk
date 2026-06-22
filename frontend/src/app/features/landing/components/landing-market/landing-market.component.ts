import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18nService } from '../../../../core/services/i18n.service';
import { RevealDirective } from '../../reveal.directive';
import { LP2_MKT_CARDS } from '../../data/market-cards';
import { pickLang } from '../../data/landing.util';
import { LandingInstallBtnComponent } from './landing-install-btn.component';

/** Marketplace preview: six listing cards with ratings, installs, price and install CTA. */
@Component({
  selector: 'app-landing-market',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RevealDirective, LandingInstallBtnComponent],
  template: `
    <section class="lp2-section" id="market" [style.padding-top]="'0'">
      <div class="lp2-wrap">
        <div class="lp2-section-head">
          <span class="lp2-eyebrow" appReveal>{{ i18n.t('p2_mkt_eyebrow') }}</span>
          <h2 class="lp2-title" appReveal [style.--rv-d]="'0.06s'">{{ i18n.t('p2_mkt_title') }}</h2>
          <p class="lp2-section-sub" appReveal [style.--rv-d]="'0.12s'">{{ i18n.t('p2_mkt_sub') }}</p>
        </div>
        <div class="lp2-mkt-grid">
          @for (c of cards; track $index; let i = $index) {
            <div class="lp2-mcard" appReveal [style.--rv-d]="delay(i)">
              <div class="lp2-mcard-top">
                <span class="lp2-mcard-cat">{{ c.cat }}</span>
                <span class="lp2-mcard-rating">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2l2.4 7.2H22l-6 4.4 2.4 7.2L12 16.4 5.6 20.8 8 13.6 2 9.2h7.6z"/></svg>{{ c.rating }}
                </span>
              </div>
              <h3>{{ pick(c.title) }}</h3>
              <p class="desc">{{ pick(c.desc) }}</p>
              <div class="lp2-mcard-meta">
                <span class="av" [style.background]="c.grad">{{ c.av }}</span>
                {{ c.by }}
                <span [style.margin-left]="'auto'">{{ c.installs }} {{ i18n.t('p2_mkt_installs') }}</span>
              </div>
              <div class="lp2-mcard-foot">
                <span class="lp2-mcard-price">
                  @if (c.price === 0) {
                    {{ i18n.t('p2_mkt_free') }}
                  } @else {
                    {{ c.price }} € <small>/ {{ i18n.lang() === 'de' ? 'Monat' : 'mo' }}</small>
                  }
                </span>
                <app-landing-install-btn />
              </div>
            </div>
          }
        </div>
        <div class="lp2-mkt-more" appReveal>
          <a class="lp2-link-arrow" routerLink="/auth/register">
            {{ i18n.t('p2_mkt_cta') }}
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 5l7 7-7 7"/></svg>
          </a>
        </div>
      </div>
    </section>
  `,
})
export class LandingMarketComponent {
  protected i18n = inject(I18nService);
  protected cards = LP2_MKT_CARDS;

  protected pick = (v: { de: string; en: string }) => pickLang(v, this.i18n.lang());
  protected delay = (i: number) => `${0.08 + (i % 3) * 0.08}s`;
}
