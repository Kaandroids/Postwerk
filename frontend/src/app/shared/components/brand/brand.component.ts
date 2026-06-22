import { ChangeDetectionStrategy, Component, inject, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18nService } from '../../../core/services/i18n.service';
import { ThemeService } from '../../../core/services/theme.service';

/** Brand logo and name link that adapts its logo asset to the current theme. */
@Component({
  selector: 'app-brand',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <a class="brand" routerLink="/">
      <img [src]="logoSrc()" alt="Postwerk" class="brand-logo" />
      <span class="brand-name">{{ i18n.t('brandName') }}</span>
    </a>
  `,
  styles: `
    .brand {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      font-weight: 600;
      letter-spacing: -0.01em;
      text-decoration: none;
      color: var(--fg);
    }
    .brand-logo {
      width: 28px;
      height: 28px;
      object-fit: contain;
    }
    .brand-name { font-size: 16px; }
  `,
})
export class BrandComponent {
  protected i18n = inject(I18nService);
  private theme = inject(ThemeService);

  protected logoSrc = computed(() =>
    this.theme.theme() === 'dark' ? 'logo-dark.svg' : 'logo-light.svg'
  );
}
