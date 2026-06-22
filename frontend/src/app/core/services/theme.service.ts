import { Injectable, signal, effect } from '@angular/core';

export type Theme = 'light' | 'dark';
export type DarkVariant = 'slate' | 'warm' | 'plum' | 'black';

/**
 * Manages the application theme (light/dark) and dark-mode variant (slate, warm, plum, black).
 *
 * Persists preferences to localStorage and synchronizes them to the document root element.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(this.loadTheme());
  readonly darkVariant = signal<DarkVariant>(this.loadVariant());

  constructor() {
    effect(() => {
      const t = this.theme();
      const v = this.darkVariant();
      const html = document.documentElement;

      html.setAttribute('data-theme', t);
      if (t === 'dark') {
        html.setAttribute('data-dark-variant', v);
      } else {
        html.removeAttribute('data-dark-variant');
      }

      localStorage.setItem('pw-theme', t);
      localStorage.setItem('pw-dark-variant', v);
    });
  }

  toggle(): void {
    this.theme.update(t => t === 'light' ? 'dark' : 'light');
  }

  setVariant(v: DarkVariant): void {
    this.darkVariant.set(v);
  }

  private loadTheme(): Theme {
    const stored = localStorage.getItem('pw-theme');
    if (stored === 'light' || stored === 'dark') return stored;
    return 'light';
  }

  private loadVariant(): DarkVariant {
    const stored = localStorage.getItem('pw-dark-variant');
    if (['slate', 'warm', 'plum', 'black'].includes(stored!)) return stored as DarkVariant;
    return 'warm';
  }
}
