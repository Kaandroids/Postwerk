import { ChangeDetectionStrategy, Component, HostListener, ViewEncapsulation, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18nService } from '../../../../core/services/i18n.service';
import { BrandComponent } from '../../../../shared/components/brand/brand.component';
import { LangSwitcherComponent } from '../../../../shared/components/lang-switcher/lang-switcher.component';
import { ThemeToggleComponent } from '../../../../shared/components/theme-toggle/theme-toggle.component';

/** Fixed top navigation with scroll progress bar, blur-on-scroll, anchors and auth links. */
@Component({
  selector: 'app-landing-nav',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, BrandComponent, LangSwitcherComponent, ThemeToggleComponent],
  encapsulation: ViewEncapsulation.None,
  template: `
    <div class="lp2-progress" [style.width.%]="progress()"></div>
    <nav class="lp2-nav" [attr.data-scrolled]="scrolled() ? '1' : '0'">
      <div class="lp2-nav-inner">
        <a routerLink="/landing"><app-brand /></a>
        <div class="lp2-nav-right">
          <div class="lp2-nav-prefs">
            <app-lang-switcher />
            <app-theme-toggle />
          </div>
          <a class="lp2-signin" routerLink="/auth/login">{{ i18n.t('lp_nav_signin') }}</a>
          <a class="lp2-nav-btn" routerLink="/auth/register">{{ i18n.t('lp_nav_start') }}</a>
        </div>
      </div>
    </nav>
  `,
})
export class LandingNavComponent {
  protected i18n = inject(I18nService);
  protected scrolled = signal(false);
  protected progress = signal(0);

  @HostListener('window:scroll')
  onScroll(): void {
    const y = window.scrollY;
    this.scrolled.set(y > 12);
    const h = document.documentElement.scrollHeight - window.innerHeight;
    this.progress.set(h > 0 ? (y / h) * 100 : 0);
  }
}
