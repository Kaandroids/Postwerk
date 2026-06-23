import { Injectable, Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { map } from 'rxjs/operators';

/**
 * Reactive viewport size, exposed as signals for use where CSS media queries
 * aren't enough (e.g. defaulting the sidebar drawer closed, switching a table
 * to a card view).
 *
 * Thresholds mirror the canonical scale in `src/styles/_breakpoints.scss`:
 *   mobile  < md (768)        — phones + portrait tablets
 *   tablet  md..lg (768–1024) — tablet band
 *   desktop ≥ lg (1024)
 *
 * Keep these queries in sync with the SCSS breakpoints.
 */
@Injectable({ providedIn: 'root' })
export class ViewportService {
  private bo = inject(BreakpointObserver);

  /** True below md (768px) — phones and portrait tablets. */
  readonly isMobile = this.match('(max-width: 767.98px)');

  /** True within the tablet band — md (768) up to lg (1024). */
  readonly isTablet = this.match('(min-width: 768px) and (max-width: 1023.98px)');

  /** True below the desktop rail — anything narrower than lg (1024px). */
  readonly isTabletDown = this.match('(max-width: 1023.98px)');

  /** True at/above lg (1024px) — the persistent-sidebar desktop layout. */
  readonly isDesktop = this.match('(min-width: 1024px)');

  private match(query: string): Signal<boolean> {
    return toSignal(this.bo.observe(query).pipe(map(r => r.matches)), {
      initialValue: this.bo.isMatched(query),
    });
  }
}
