import {
  ChangeDetectionStrategy, Component, DestroyRef, ElementRef,
  afterNextRender, inject, signal,
} from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';

/** Stat ticker: four metrics that count up once the band scrolls into view. */
@Component({
  selector: 'app-landing-ticker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="lp2-ticker" id="stats">
      <div class="lp2-ticker-inner">
        <div class="lp2-ticker-cell">
          <span class="lp2-ticker-num"><em>{{ v1().toFixed(1) }}h</em></span>
          <span class="lp2-ticker-label">{{ i18n.t('p2_stat1') }}</span>
        </div>
        <div class="lp2-ticker-cell">
          <span class="lp2-ticker-num">{{ v2().toFixed(0) }}<em>+</em></span>
          <span class="lp2-ticker-label">{{ i18n.t('p2_stat2') }}</span>
        </div>
        <div class="lp2-ticker-cell">
          <span class="lp2-ticker-num">{{ v3().toFixed(1) }}<em>M</em></span>
          <span class="lp2-ticker-label">{{ i18n.t('p2_stat3') }}</span>
        </div>
        <div class="lp2-ticker-cell">
          <span class="lp2-ticker-num">{{ v4().toFixed(0) }}<em>min</em></span>
          <span class="lp2-ticker-label">{{ i18n.t('p2_stat4') }}</span>
        </div>
      </div>
    </div>
  `,
})
export class LandingTickerComponent {
  protected i18n = inject(I18nService);
  private host = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
  private destroyRef = inject(DestroyRef);

  protected v1 = signal(0);
  protected v2 = signal(0);
  protected v3 = signal(0);
  protected v4 = signal(0);

  constructor() {
    afterNextRender(() => this.observe());
  }

  private observe(): void {
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced) { this.set(4.2, 480, 2.1, 4); return; }

    let started = false;
    let raf = 0;
    const run = () => {
      if (started) return;
      started = true;
      const t0 = performance.now();
      const tick = (now: number) => {
        const p = Math.min(1, (now - t0) / 1400);
        const e = 1 - Math.pow(1 - p, 3);
        this.set(4.2 * e, 480 * e, 2.1 * e, 4 * e);
        if (p < 1) raf = requestAnimationFrame(tick);
      };
      raf = requestAnimationFrame(tick);
      cleanup();
    };
    const check = () => {
      const vh = window.innerHeight || document.documentElement.clientHeight;
      if (this.host.getBoundingClientRect().top < vh * 0.88) run();
    };
    let io: IntersectionObserver | null = null;
    if (typeof IntersectionObserver !== 'undefined') {
      io = new IntersectionObserver((es) => { if (es.some((e) => e.isIntersecting)) run(); }, { threshold: 0.15 });
      io.observe(this.host);
    }
    window.addEventListener('scroll', check, { passive: true });
    window.addEventListener('resize', check);
    const cleanup = () => {
      io?.disconnect();
      window.removeEventListener('scroll', check);
      window.removeEventListener('resize', check);
    };
    this.destroyRef.onDestroy(() => { cleanup(); cancelAnimationFrame(raf); });
    check();
  }

  private set(a: number, b: number, c: number, d: number): void {
    this.v1.set(a); this.v2.set(b); this.v3.set(c); this.v4.set(d);
  }
}
