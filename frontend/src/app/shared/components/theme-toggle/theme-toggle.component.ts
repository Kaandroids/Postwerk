import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ThemeService } from '../../../core/services/theme.service';
import { IconComponent } from '../icon/icon.component';

/** Icon button that toggles the application between light and dark themes. */
@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <button
      type="button"
      class="icon-btn"
      [attr.aria-label]="theme.theme() === 'dark' ? 'Light mode' : 'Dark mode'"
      (click)="theme.toggle()"
    >
      @if (theme.theme() === 'dark') {
        <app-icon name="sun" />
      } @else {
        <app-icon name="moon" />
      }
    </button>
  `,
  styles: `
    .icon-btn {
      appearance: none;
      border: 0.5px solid var(--border);
      background: var(--bg);
      width: 34px;
      height: 34px;
      border-radius: 8px;
      color: var(--fg-muted);
      cursor: pointer;
      display: grid;
      place-items: center;
      transition: background 0.12s, color 0.12s;
    }
    .icon-btn:hover { background: var(--bg-2); color: var(--fg); }
  `,
})
export class ThemeToggleComponent {
  protected theme = inject(ThemeService);
}
