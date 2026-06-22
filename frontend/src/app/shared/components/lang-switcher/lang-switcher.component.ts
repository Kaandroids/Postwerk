import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { I18nService, Lang } from '../../../core/services/i18n.service';

/** Segmented toggle for switching the application language between German and English. */
@Component({
  selector: 'app-lang-switcher',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="seg" role="radiogroup" aria-label="Language">
      @for (l of langs; track l) {
        <button
          type="button"
          role="radio"
          [attr.aria-checked]="i18n.lang() === l"
          [attr.data-active]="i18n.lang() === l ? '1' : '0'"
          (click)="i18n.setLang(l)"
        >{{ i18n.t('lang_' + l) }}</button>
      }
    </div>
  `,
  styles: `
    .seg {
      display: inline-flex;
      padding: 3px;
      background: var(--bg-2);
      border: 0.5px solid var(--border);
      border-radius: 999px;
      font-size: 12px;
      font-weight: 500;
      letter-spacing: 0.02em;
    }
    button {
      appearance: none;
      border: 0;
      background: transparent;
      padding: 5px 12px;
      border-radius: 999px;
      color: var(--fg-muted);
      cursor: pointer;
      transition: color 0.15s, background 0.15s;
    }
    button[data-active="1"] {
      color: var(--fg);
      background: var(--bg);
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
    }
    button:hover { color: var(--fg); }
  `,
})
export class LangSwitcherComponent {
  protected i18n = inject(I18nService);
  protected langs: Lang[] = ['de', 'en'];
}
