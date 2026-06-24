import { ChangeDetectionStrategy, Component, DestroyRef, HostListener, ViewEncapsulation, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { I18nService } from '../../../../core/services/i18n.service';
import { BrandComponent } from '../../../../shared/components/brand/brand.component';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { LangSwitcherComponent } from '../../../../shared/components/lang-switcher/lang-switcher.component';
import { ThemeToggleComponent } from '../../../../shared/components/theme-toggle/theme-toggle.component';

/** Fixed top navigation with scroll progress bar, blur-on-scroll, Home/Docs links and auth links.
 *  Below the desktop rail the links collapse into a slide-in drawer (hamburger). */
@Component({
  selector: 'app-landing-nav',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, BrandComponent, IconComponent, LangSwitcherComponent, ThemeToggleComponent],
  encapsulation: ViewEncapsulation.None,
  template: `
    <div class="lp2-progress" [style.width.%]="progress()"></div>
    <nav class="lp2-nav" [attr.data-scrolled]="scrolled() ? '1' : '0'">
      <div class="lp2-nav-inner">
        <button class="lp2-nav-burger" type="button" (click)="drawerOpen.set(true)"
                [attr.aria-label]="i18n.t('lp_nav_menu')" aria-haspopup="dialog">
          <app-icon name="menu" />
        </button>

        <a class="lp2-nav-brand" routerLink="/landing"><app-brand /></a>

        <div class="lp2-nav-links">
          <a routerLink="/landing" routerLinkActive="is-on" [routerLinkActiveOptions]="{ exact: true }">{{ i18n.t('lp_nav_home') }}</a>
          <a routerLink="/docs" routerLinkActive="is-on">{{ i18n.t('lp_nav_docs') }}</a>
        </div>

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

    <div class="lp2-drawer" [class.is-open]="drawerOpen()" [attr.aria-hidden]="drawerOpen() ? null : 'true'">
      <div class="lp2-drawer-scrim" (click)="closeDrawer()"></div>
      <aside class="lp2-drawer-panel" role="dialog" aria-modal="true">
        <div class="lp2-drawer-head">
          <app-brand />
          <button class="lp2-drawer-x" type="button" (click)="closeDrawer()" [attr.aria-label]="i18n.t('lp_nav_close')">
            <app-icon name="close" />
          </button>
        </div>

        <nav class="lp2-drawer-nav">
          <a routerLink="/landing" routerLinkActive="is-on" [routerLinkActiveOptions]="{ exact: true }" (click)="closeDrawer()">{{ i18n.t('lp_nav_home') }}</a>
          <a routerLink="/docs" routerLinkActive="is-on" (click)="closeDrawer()">{{ i18n.t('lp_nav_docs') }}</a>
        </nav>

        <div class="lp2-drawer-foot">
          <a class="lp2-signin" routerLink="/auth/login" (click)="closeDrawer()">{{ i18n.t('lp_nav_signin') }}</a>
          <a class="lp2-nav-btn lp2-drawer-cta" routerLink="/auth/register" (click)="closeDrawer()">{{ i18n.t('lp_nav_start') }}</a>
          <div class="lp2-drawer-prefs">
            <app-lang-switcher />
            <app-theme-toggle />
          </div>
        </div>
      </aside>
    </div>
  `,
})
export class LandingNavComponent {
  protected i18n = inject(I18nService);
  private router = inject(Router);
  protected scrolled = signal(false);
  protected progress = signal(0);
  protected drawerOpen = signal(false);

  constructor() {
    // Close the drawer on every navigation (link tap routes via the SPA).
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      takeUntilDestroyed(inject(DestroyRef)),
    ).subscribe(() => this.drawerOpen.set(false));
  }

  protected closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  @HostListener('window:scroll')
  onScroll(): void {
    const y = window.scrollY;
    this.scrolled.set(y > 12);
    const h = document.documentElement.scrollHeight - window.innerHeight;
    this.progress.set(h > 0 ? (y / h) * 100 : 0);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.drawerOpen()) this.drawerOpen.set(false);
  }
}
