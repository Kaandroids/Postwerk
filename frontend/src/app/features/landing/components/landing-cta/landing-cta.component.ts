import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18nService } from '../../../../core/services/i18n.service';
import { RevealDirective } from '../../reveal.directive';

/** Closing CTA band: framed headline, sub copy and primary / sales buttons. */
@Component({
  selector: 'app-landing-cta',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RevealDirective],
  template: `
    <section class="lp2-cta-band">
      <div class="lp2-wrap">
        <div class="lp2-cta-frame" appReveal>
          <span class="mark">P</span>
          <h2>{{ i18n.t('p2_cta_a') }} <em>{{ i18n.t('p2_cta_em') }}</em></h2>
          <p>{{ i18n.t('p2_cta_sub') }}</p>
          <div class="lp2-cta-row">
            <a class="lp2-cta-primary" routerLink="/auth/register">
              {{ i18n.t('lp_cta_start') }}
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 5l7 7-7 7"/></svg>
            </a>
            <button class="lp2-cta-ghost">{{ i18n.t('lp_cta_band_sales') }}</button>
          </div>
        </div>
      </div>
    </section>
  `,
})
export class LandingCtaComponent {
  protected i18n = inject(I18nService);
}
