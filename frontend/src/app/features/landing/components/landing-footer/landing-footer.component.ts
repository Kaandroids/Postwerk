import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18nService } from '../../../../core/services/i18n.service';
import { BrandComponent } from '../../../../shared/components/brand/brand.component';

/** Landing v2 footer: brand blurb, product/legal links and copyright row. */
@Component({
  selector: 'app-landing-footer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, BrandComponent],
  styleUrl: './landing-footer.component.scss',
  template: `
    <footer class="lp2-footer">
      <div class="lp2-wrap">
        <div class="lp2-footer-inner">
          <div class="lp2-footer-brand">
            <app-brand />
            <p>{{ i18n.t('lp_footer_about') }}</p>
          </div>
          <div class="lp2-footer-links">
            <a routerLink="/landing" fragment="market">{{ i18n.t('p2_nav_market') }}</a>
            <a routerLink="/landing" fragment="studio">{{ i18n.t('p2_nav_pricing') }}</a>
            <a routerLink="/legal/datenschutz">{{ i18n.t('legal_privacy') }}</a>
            <a routerLink="/legal/impressum">{{ i18n.t('legal_imprint') }}</a>
            <a routerLink="/legal/agb">{{ i18n.t('legal_terms') }}</a>
          </div>
        </div>
        <div class="lp2-footer-bottom">
          <span>{{ i18n.t('lp_footer_copy') }}</span>
          <span>{{ i18n.t('p2_footer_tag') }}</span>
        </div>
      </div>
    </footer>
  `,
})
export class LandingFooterComponent {
  protected i18n = inject(I18nService);
}
