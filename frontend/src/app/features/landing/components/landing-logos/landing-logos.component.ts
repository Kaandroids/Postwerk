import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';

/** Trusted-by marquee: a duplicated row of brand names scrolling horizontally. */
@Component({
  selector: 'app-landing-logos',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="lp2-logos">
      <div class="lp2-wrap">
        <div class="lp2-logos-eyebrow">{{ i18n.t('lp_logos_eyebrow') }}</div>
      </div>
      <div class="lp2-marquee">
        <div class="lp2-marquee-track">
          @for (n of row; track $index) {
            <span>{{ n }}</span>
          }
        </div>
      </div>
    </section>
  `,
})
export class LandingLogosComponent {
  protected i18n = inject(I18nService);
  private names = ['Lumen & Co.', 'Northwind', 'Hyperion', 'Procure AI', 'Kestrel', 'Brightline', 'Veldt', 'Marrow'];
  protected row = [...this.names, ...this.names];
}
