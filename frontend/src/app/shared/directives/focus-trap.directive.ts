import { AfterViewInit, Directive, ElementRef, OnDestroy, inject } from '@angular/core';

/**
 * Accessible modal focus management. On a `role="dialog"` element, moves focus into the dialog on
 * open, keeps Tab/Shift+Tab cycling within it, and restores focus to the previously-focused element
 * on close. Pair with the existing Escape-to-close + scrim handlers the admin modals already have.
 */
@Directive({
  selector: '[appFocusTrap]',
  standalone: true,
})
export class FocusTrapDirective implements AfterViewInit, OnDestroy {
  private host = inject<ElementRef<HTMLElement>>(ElementRef);
  private previouslyFocused: HTMLElement | null = null;
  private readonly onKeydown = (e: KeyboardEvent) => this.trap(e);

  private static readonly SELECTOR =
    'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), ' +
    'textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

  ngAfterViewInit(): void {
    const el = this.host.nativeElement;
    this.previouslyFocused = document.activeElement as HTMLElement | null;
    el.addEventListener('keydown', this.onKeydown);
    const items = this.focusable();
    if (items.length > 0) {
      items[0].focus();
    } else {
      el.tabIndex = -1;
      el.focus();
    }
  }

  ngOnDestroy(): void {
    this.host.nativeElement.removeEventListener('keydown', this.onKeydown);
    this.previouslyFocused?.focus?.();
  }

  private focusable(): HTMLElement[] {
    return Array.from(
      this.host.nativeElement.querySelectorAll<HTMLElement>(FocusTrapDirective.SELECTOR),
    ).filter(el => el.offsetParent !== null);
  }

  private trap(e: KeyboardEvent): void {
    if (e.key !== 'Tab') return;
    const items = this.focusable();
    if (items.length === 0) return;
    const first = items[0];
    const last = items[items.length - 1];
    const active = document.activeElement;
    if (e.shiftKey && active === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && active === last) {
      e.preventDefault();
      first.focus();
    }
  }
}
