import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18nService } from '../../../../core/services/i18n.service';
import { RevealDirective } from '../../reveal.directive';
import { Bi, pickLang } from '../../data/landing.util';

/** Supervised mode section: a three-step "how approval works" rail beside sample approval-queue cards. */
@Component({
  selector: 'app-landing-supervised',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RevealDirective],
  template: `
    <section class="lp2-section" id="supervised" [style.padding-top]="'0'">
      <div class="lp2-wrap">
        <div class="lp2-section-head">
          <span class="lp2-eyebrow" appReveal>{{ i18n.t('p2_sup_eyebrow') }}</span>
          <h2 class="lp2-title" appReveal [style.--rv-d]="'0.06s'">{{ i18n.t('p2_sup_title') }}</h2>
          <p class="lp2-section-sub" appReveal [style.--rv-d]="'0.12s'">{{ i18n.t('p2_sup_sub') }}</p>
        </div>
        <div class="lp2-exp-grid">
          <div class="lp2-exp-steps">
            @for (n of [1, 2, 3]; track n; let i = $index) {
              <div class="lp2-exp-step" appReveal [style.--rv-d]="stepDelay(i)">
                <span class="pin"></span>
                <h4>{{ i18n.t('p2_sup_s' + n + '_t') }}</h4>
                <p>{{ i18n.t('p2_sup_s' + n + '_b') }}</p>
              </div>
            }
            <div appReveal [style.--rv-d]="'0.4s'" [style.padding-left.px]="34">
              <a class="lp2-link-arrow" routerLink="/auth/register">
                {{ i18n.t('p2_sup_cta') }}
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 5l7 7-7 7"/></svg>
              </a>
            </div>
          </div>
          <div class="lp2-exp-cards">
            @for (s of samples; track s; let i = $index) {
              <div class="lp2-xcard" appReveal [style.--rv-d]="cardDelay(i)">
                <div class="lp2-xcard-top">
                  <span class="av" [style.background]="'linear-gradient(135deg,#cfe9d6,#7eb692)'">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#1c3a28" stroke-width="2.4"><path d="M20 6L9 17l-5-5"/></svg>
                  </span>
                  <span>
                    <span class="nm" [style.display]="'block'">{{ pick(s.subj) }}</span>
                    <span class="rl" [style.display]="'block'">{{ i18n.t('p2_sup_propose') }}: {{ pick(s.act) }}</span>
                  </span>
                </div>
                <div class="lp2-xcard-meta">
                  <span class="pr">{{ i18n.t('p2_sup_approve') }}</span>
                  <span>{{ i18n.t('p2_sup_reject') }}</span>
                </div>
              </div>
            }
          </div>
        </div>
      </div>
    </section>
  `,
})
export class LandingSupervisedComponent {
  protected i18n = inject(I18nService);

  /** Sample pending actions shown in the approval-queue cards. */
  protected samples: { subj: Bi; act: Bi }[] = [
    { subj: { de: 'Rechnung #4021 · Acme GmbH', en: 'Invoice #4021 · Acme Ltd' }, act: { de: 'Weiterleiten an Buchhaltung', en: 'Forward to accounting' } },
    { subj: { de: 'Anfrage · 200 Plätze', en: 'Inquiry · 200 seats' }, act: { de: 'Antwort senden (Vorlage)', en: 'Send reply (template)' } },
    { subj: { de: 'Neuer Lead · ProcureAI', en: 'New lead · ProcureAI' }, act: { de: 'Im CRM anlegen', en: 'Create in CRM' } },
  ];

  protected pick = (v: Bi) => pickLang(v, this.i18n.lang());
  protected stepDelay = (i: number) => `${0.1 + i * 0.1}s`;
  protected cardDelay = (i: number) => `${0.08 + i * 0.08}s`;
}
