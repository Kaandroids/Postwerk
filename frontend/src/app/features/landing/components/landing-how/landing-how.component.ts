import {
  ChangeDetectionStrategy, Component, DestroyRef, ElementRef,
  afterNextRender, inject, signal, viewChild,
} from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { RevealDirective } from '../../reveal.directive';

/** How-it-works: a sticky copy rail beside three scroll-driven visual panels. */
@Component({
  selector: 'app-landing-how',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RevealDirective],
  template: `
    <section class="lp2-section" id="how">
      <div class="lp2-wrap">
        <div class="lp2-how-grid">
          <div class="lp2-how-rail">
            <span class="lp2-eyebrow" appReveal>{{ i18n.t('p2_how_eyebrow') }}</span>
            <h2 class="lp2-title" appReveal [style.--rv-d]="'0.06s'">{{ i18n.t('p2_how_title') }}</h2>
            <p class="lp2-section-sub" appReveal [style.--rv-d]="'0.12s'">{{ i18n.t('p2_how_sub') }}</p>
            <div class="lp2-how-steps">
              @for (n of [1, 2, 3]; track n) {
                <div class="lp2-how-step" [attr.data-active]="active() === n - 1 ? '1' : '0'">
                  <span class="n">0{{ n }}</span>
                  <h3>{{ i18n.t('p2_how' + n + '_t') }}</h3>
                  <p>{{ i18n.t('p2_how' + n + '_b') }}</p>
                </div>
              }
            </div>
          </div>
          <div class="lp2-how-visuals" #visuals>
            <div class="lp2-how-panel" appReveal [attr.data-active]="active() === 0 ? '1' : '0'">
              <span class="cap">{{ i18n.t('p2_how1_t') }}</span>
              <div class="lp2-bigtype">
                <span class="kw">»</span>
                @if (i18n.lang() === 'de') {
                  Wenn eine <span class="str">Bestellung über 500 €</span> reinkommt, <span class="str">lege sie in Notion an</span> und <span class="str">benachrichtige #sales</span>.
                } @else {
                  When an <span class="str">order over €500</span> comes in, <span class="str">add it to Notion</span> and <span class="str">notify #sales</span>.
                }
                <span class="kw"> «</span>
                <span class="lp2-cursor"></span>
              </div>
            </div>
            <div class="lp2-how-panel" appReveal [attr.data-active]="active() === 1 ? '1' : '0'">
              <span class="cap">{{ i18n.t('p2_how2_t') }}</span>
              <div class="lp2-scan">
                <i data-w="1" [style.--i]="0"></i>
                <i data-w="3" [style.--i]="1"></i>
                <i data-w="2" [style.--i]="2"></i>
                <i data-w="4" [style.--i]="3"></i>
                <i data-w="2" [style.--i]="4"></i>
              </div>
            </div>
            <div class="lp2-how-panel" appReveal [attr.data-active]="active() === 2 ? '1' : '0'">
              <span class="cap">{{ i18n.t('p2_how3_t') }}</span>
              <div class="lp2-chips">
                @for (c of chips; track c; let i = $index) {
                  <span [style.--i]="i">{{ c }}</span>
                }
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  `,
})
export class LandingHowComponent {
  protected i18n = inject(I18nService);
  private destroyRef = inject(DestroyRef);
  private visuals = viewChild.required<ElementRef<HTMLElement>>('visuals');

  protected active = signal(0);
  protected chips = ['E-Mail', 'Slack', 'Notion', 'HubSpot', 'Shopify', 'Kalender', 'DATEV', 'Webhook'];

  constructor() {
    afterNextRender(() => this.bind());
  }

  private bind(): void {
    const onScroll = () => {
      const el = this.visuals().nativeElement;
      const panels = Array.from(el.children) as HTMLElement[];
      const mid = (window.innerHeight || 800) * 0.5;
      let best = 0;
      let bestDist = Infinity;
      panels.forEach((p, i) => {
        const r = p.getBoundingClientRect();
        const c = r.top + r.height / 2;
        const d = Math.abs(c - mid);
        if (d < bestDist) { bestDist = d; best = i; }
      });
      this.active.set(best);
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
    this.destroyRef.onDestroy(() => window.removeEventListener('scroll', onScroll));
  }
}
