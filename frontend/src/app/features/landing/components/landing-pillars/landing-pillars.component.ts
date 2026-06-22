import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { RevealDirective } from '../../reveal.directive';

/** Three product pillars linking to the on-page chat / market / supervised anchors. */
@Component({
  selector: 'app-landing-pillars',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RevealDirective],
  template: `
    <section class="lp2-section" id="pillars">
      <div class="lp2-wrap">
        <div class="lp2-section-head">
          <span class="lp2-eyebrow" appReveal>{{ i18n.t('p2_pillars_eyebrow') }}</span>
          <h2 class="lp2-title" appReveal [style.--rv-d]="'0.06s'">{{ i18n.t('p2_pillars_title') }}</h2>
          <p class="lp2-section-sub" appReveal [style.--rv-d]="'0.12s'">{{ i18n.t('p2_pillars_sub') }}</p>
        </div>
        <div class="lp2-pillars">
          @for (p of pillars; track p.n) {
            <a class="lp2-pillar" appReveal [href]="p.link" [style.--rv-d]="p.d">
              <span class="num">0{{ p.n }}</span>
              <span class="ic">
                <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path [attr.d]="p.icon"/></svg>
              </span>
              <h3>{{ i18n.t('p2_pillar' + p.n + '_title') }}</h3>
              <p>{{ i18n.t('p2_pillar' + p.n + '_body') }}</p>
              <span class="go">
                {{ i18n.t('p2_pillar_link') }}
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 5l7 7-7 7"/></svg>
              </span>
            </a>
          }
        </div>
      </div>
    </section>
  `,
})
export class LandingPillarsComponent {
  protected i18n = inject(I18nService);
  protected pillars = [
    { n: 1, link: '#chat', d: '0.1s', icon: 'M21 11.5a8.38 8.38 0 01-.9 3.8 8.5 8.5 0 01-7.6 4.7 8.38 8.38 0 01-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 01-.9-3.8 8.5 8.5 0 014.7-7.6 8.38 8.38 0 013.8-.9h.5a8.48 8.48 0 018 8v.5z' },
    { n: 2, link: '#market', d: '0.2s', icon: 'M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4zM3 6h18M16 10a4 4 0 01-8 0' },
    { n: 3, link: '#supervised', d: '0.3s', icon: 'M9 12l2 2 4-4M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z' },
  ];
}
