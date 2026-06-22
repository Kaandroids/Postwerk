import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18nService } from '../../../../core/services/i18n.service';
import { LandingConsoleComponent } from '../landing-console/landing-console.component';

/** A single revealed word in the headline mask animation. */
interface HeadlineWord {
  w: string;
  em: boolean;
  d: string;
}

/** Editorial hero: eyebrow, word-by-word headline, sub copy, CTAs, trust + autopilot console. */
@Component({
  selector: 'app-landing-hero',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, LandingConsoleComponent],
  template: `
    <header class="lp2-hero">
      <div class="lp2-wrap">
        <div class="lp2-hero-grid">
          <div>
            <span class="lp2-eyebrow lp2-fade-up">{{ i18n.t('p2_pill') }}</span>
            <h1 class="lp2-h1">
              @for (x of words(); track $index) {
                <span class="w" [attr.data-em]="x.em ? '1' : '0'"><i [style.--d]="x.d">{{ x.w }}</i></span>
                @if (!$last) { {{ ' ' }} }
              }
            </h1>
            <p class="lp2-sub lp2-fade-up" [style.--d]="'0.4s'">{{ i18n.t('p2_sub') }}</p>
            <div class="lp2-cta-row lp2-fade-up" [style.--d]="'0.5s'">
              <a class="lp2-cta-primary" routerLink="/auth/register">
                {{ i18n.t('lp_cta_start') }}
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 5l7 7-7 7"/></svg>
              </a>
              <button class="lp2-cta-ghost">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                {{ i18n.t('lp_cta_demo') }}
              </button>
            </div>
            <div class="lp2-trust lp2-fade-up" [style.--d]="'0.6s'">
              <span><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M20 6L9 17l-5-5"/></svg>{{ i18n.t('p2_trust1') }}</span>
              <span><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M20 6L9 17l-5-5"/></svg>{{ i18n.t('p2_trust2') }}</span>
              <span><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M20 6L9 17l-5-5"/></svg>{{ i18n.t('p2_trust3') }}</span>
            </div>
          </div>
          <app-landing-console />
        </div>
      </div>
    </header>
  `,
})
export class LandingHeroComponent {
  protected i18n = inject(I18nService);

  protected words = computed<HeadlineWord[]>(() => {
    this.i18n.lang();
    const out: HeadlineWord[] = this.i18n
      .t('p2_h_a')
      .trim()
      .split(/\s+/)
      .map((w, i) => ({ w, em: false, d: `${0.08 + i * 0.07}s` }));
    out.push({ w: this.i18n.t('p2_h_em'), em: true, d: `${0.08 + out.length * 0.07}s` });
    return out;
  });
}
