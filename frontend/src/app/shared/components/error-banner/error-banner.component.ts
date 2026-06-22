import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

/** Inline error message banner with an alert icon, shown when the message input is non-empty. */
@Component({
  selector: 'app-error-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (message()) {
      <div class="banner" aria-live="assertive" role="alert">
        <app-icon name="alert" />
        <span>{{ message() }}</span>
      </div>
    }
  `,
  styles: `
    .banner {
      padding: 12px 14px;
      border-radius: var(--radius);
      background: oklch(0.58 0.20 25 / 0.08);
      border: 0.5px solid oklch(0.58 0.20 25 / 0.25);
      color: var(--danger);
      font-size: 13.5px;
      display: flex;
      align-items: flex-start;
      gap: 10px;
      margin-bottom: 4px;
    }
    :host-context([data-theme="dark"]) .banner {
      background: oklch(0.58 0.20 25 / 0.12);
    }
  `,
})
export class ErrorBannerComponent {
  message = input('');
}
