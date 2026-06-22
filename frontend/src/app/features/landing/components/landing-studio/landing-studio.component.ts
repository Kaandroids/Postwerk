import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { RevealDirective } from '../../reveal.directive';
import { LP2_RULES } from '../../data/studio-rules';
import { VIA_KEY, pickLang } from '../../data/landing.util';

/** Automation studio: pick a use-case template, see its generated rule + recent runs. */
@Component({
  selector: 'app-landing-studio',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RevealDirective],
  template: `
    <section class="lp2-section" id="studio" [style.padding-top]="'0'">
      <div class="lp2-wrap">
        <div class="lp2-section-head">
          <span class="lp2-eyebrow" appReveal>{{ i18n.t('p2_studio_eyebrow') }}</span>
          <h2 class="lp2-title" appReveal [style.--rv-d]="'0.06s'">{{ i18n.t('p2_studio_title') }}</h2>
          <p class="lp2-section-sub" appReveal [style.--rv-d]="'0.12s'">{{ i18n.t('p2_studio_sub') }}</p>
        </div>
        <div class="lp2-studio" appReveal [style.--rv-d]="'0.15s'">
          <div class="lp2-studio-side">
            <span class="cap">{{ i18n.t('p2_studio_templates') }}</span>
            @for (r of rules; track r.id) {
              <button class="lp2-rule-pill" [attr.data-active]="active() === r.id ? '1' : '0'" (click)="active.set(r.id)">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path [attr.d]="r.icon"/></svg>
                {{ pick(r.label) }}
              </button>
            }
          </div>
          @for (r of [rule()]; track r.id) {
            <div class="lp2-studio-out">
              <span class="cap">{{ i18n.t('p2_studio_generated') }}</span>
              <div class="lp2-out-prompt">
                {{ pick(r.prompt.a) }}<span class="kw">{{ pick(r.prompt.kw) }}</span>{{ pick(r.prompt.b) }}<span class="action">{{ pick(r.prompt.act) }}</span>{{ pick(r.prompt.c) }}
              </div>
              <div class="lp2-out-div">{{ matchedLabel(r.samples.length) }}</div>
              @for (s of r.samples; track $index; let i = $index) {
                <div class="lp2-out-row" [style.--d]="rowDelay(i)">
                  <span class="lp2-via sm" [attr.data-via]="s.via">{{ i18n.t(viaKey(s.via)) }}</span>
                  <span class="who">{{ s.from }}</span>
                  <span class="what">{{ pick(s.subj) }}</span>
                  <span class="lp2-tag" [attr.data-act]="s.act">{{ pick(s.tag) }}</span>
                </div>
              }
            </div>
          }
        </div>
      </div>
    </section>
  `,
})
export class LandingStudioComponent {
  protected i18n = inject(I18nService);
  protected rules = LP2_RULES;
  protected active = signal(LP2_RULES[0].id);

  protected rule = computed(() => this.rules.find((r) => r.id === this.active()) ?? this.rules[0]);

  protected pick = (v: { de: string; en: string }) => pickLang(v, this.i18n.lang());
  protected viaKey = (v: keyof typeof VIA_KEY) => VIA_KEY[v];
  protected rowDelay = (i: number) => `${0.08 + i * 0.09}s`;
  protected matchedLabel = (n: number) => this.i18n.t('p2_studio_matched').replace('{n}', String(n));
}
