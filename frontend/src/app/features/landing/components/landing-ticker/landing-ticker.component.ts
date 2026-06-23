import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';

/** Value strip: four qualitative value statements (no fabricated metrics). */
@Component({
  selector: 'app-landing-ticker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="lp2-ticker" id="stats">
      <div class="lp2-ticker-inner">
        <div class="lp2-ticker-cell">
          <span class="lp2-ticker-lead">{{ i18n.t('p2_stat_h1') }}</span>
          <span class="lp2-ticker-label">{{ i18n.t('p2_stat1') }}</span>
        </div>
        <div class="lp2-ticker-cell">
          <span class="lp2-ticker-lead">{{ i18n.t('p2_stat_h2') }}</span>
          <span class="lp2-ticker-label">{{ i18n.t('p2_stat2') }}</span>
        </div>
        <div class="lp2-ticker-cell">
          <span class="lp2-ticker-lead">{{ i18n.t('p2_stat_h3') }}</span>
          <span class="lp2-ticker-label">{{ i18n.t('p2_stat3') }}</span>
        </div>
        <div class="lp2-ticker-cell">
          <span class="lp2-ticker-lead">{{ i18n.t('p2_stat_h4') }}</span>
          <span class="lp2-ticker-label">{{ i18n.t('p2_stat4') }}</span>
        </div>
      </div>
    </div>
  `,
})
export class LandingTickerComponent {
  protected i18n = inject(I18nService);
}
