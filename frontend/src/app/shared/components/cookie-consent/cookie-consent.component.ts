import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CookieConsentService } from '../../../core/services/cookie-consent.service';
import { I18nService } from '../../../core/services/i18n.service';
import { ButtonComponent } from '../button/button.component';
import { IconComponent } from '../icon/icon.component';

/** Fixed bottom banner prompting the user to accept all cookies or essential-only cookies. */
@Component({
  selector: 'app-cookie-consent',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent],
  template: `
    @if (!cookieConsent.hasConsented()) {
      <div class="cc-overlay">
        <div class="cc-banner" role="alertdialog" aria-modal="true" aria-label="Cookie consent">
          <div class="cc-icon">
            <app-icon name="shield" />
          </div>
          <div class="cc-content">
            <h3>{{ i18n.t('cookie_title') }}</h3>
            <p>{{ i18n.t('cookie_text') }}</p>
          </div>
          <div class="cc-actions">
            <app-button variant="ghost" (click)="cookieConsent.acceptEssentialOnly()">
              {{ i18n.t('cookie_essential_only') }}
            </app-button>
            <app-button variant="primary" (click)="cookieConsent.acceptAll()">
              {{ i18n.t('cookie_accept_all') }}
            </app-button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .cc-overlay {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      z-index: 9999;
      padding: 16px;
      pointer-events: none;
    }
    .cc-banner {
      pointer-events: auto;
      max-width: 640px;
      margin: 0 auto;
      background: var(--bg-primary);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 24px;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .cc-icon {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      background: var(--accent-bg, rgba(99, 102, 241, 0.1));
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--accent, #6366f1);
    }
    .cc-content h3 {
      margin: 0 0 4px;
      font-size: 15px;
      font-weight: 600;
      color: var(--text-primary);
    }
    .cc-content p {
      margin: 0;
      font-size: 13px;
      line-height: 1.5;
      color: var(--text-secondary);
    }
    .cc-actions {
      display: flex;
      gap: 8px;
      justify-content: flex-end;
    }
  `]
})
export class CookieConsentComponent {
  cookieConsent = inject(CookieConsentService);
  i18n = inject(I18nService);
}
