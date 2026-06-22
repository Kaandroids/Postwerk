import { DestroyRef, Directive, ElementRef, afterNextRender, inject } from '@angular/core';

/**
 * Scroll-into-view reveal: adds the `rv` style hook and flips `data-in="1"`
 * the first time the host enters the lower viewport. Pair with the `.lp2 .rv`
 * CSS transition. Per-element delay is set inline via `--rv-d` in templates.
 */
@Directive({
  selector: '[appReveal]',
  standalone: true,
  host: { class: 'rv' },
})
export class RevealDirective {
  private host = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
  private destroyRef = inject(DestroyRef);

  constructor() {
    afterNextRender(() => this.observe());
  }

  private observe(): void {
    const el = this.host;
    let done = false;
    const reveal = () => {
      if (done) return;
      done = true;
      el.setAttribute('data-in', '1');
      cleanup();
    };
    const check = () => {
      const vh = window.innerHeight || document.documentElement.clientHeight;
      if (el.getBoundingClientRect().top < vh * 0.88) reveal();
    };
    let io: IntersectionObserver | null = null;
    if (typeof IntersectionObserver !== 'undefined') {
      io = new IntersectionObserver(
        (entries) => { if (entries.some((e) => e.isIntersecting)) reveal(); },
        { threshold: 0.15 },
      );
      io.observe(el);
    }
    window.addEventListener('scroll', check, { passive: true });
    window.addEventListener('resize', check);
    const cleanup = () => {
      io?.disconnect();
      window.removeEventListener('scroll', check);
      window.removeEventListener('resize', check);
    };
    this.destroyRef.onDestroy(cleanup);
    check();
  }
}
