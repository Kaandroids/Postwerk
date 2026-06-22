import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';
import { I18nService } from '../../../../core/services/i18n.service';
import { BrandComponent } from '../../../../shared/components/brand/brand.component';
import { LangSwitcherComponent } from '../../../../shared/components/lang-switcher/lang-switcher.component';
import { ThemeToggleComponent } from '../../../../shared/components/theme-toggle/theme-toggle.component';
/** Shell layout for authentication pages, providing brand header, language switcher, and theme toggle. */
@Component({
  selector: 'app-auth-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, BrandComponent, LangSwitcherComponent, ThemeToggleComponent],
  templateUrl: './auth-layout.component.html',
  styleUrl: './auth-layout.component.scss',
})
export class AuthLayoutComponent {
  protected i18n = inject(I18nService);
}
