import { ChangeDetectionStrategy, Component, inject, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18nService } from '../../../../core/services/i18n.service';
import { TokenService } from '../../../../core/services/token.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { BrandComponent } from '../../../../shared/components/brand/brand.component';
import { LangSwitcherComponent } from '../../../../shared/components/lang-switcher/lang-switcher.component';
import { ThemeToggleComponent } from '../../../../shared/components/theme-toggle/theme-toggle.component';
import { DocsService } from '../../docs.service';

/**
 * Docs top bar. Full-bleed app-chrome header (not the marketing nav): brand,
 * a dashboard-style search trigger that opens the command palette, language +
 * theme controls, and an auth-aware right side — a Dashboard button when the
 * visitor is signed in, otherwise sign-in / register. Border is always visible.
 */
@Component({
  selector: 'app-docs-header',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, BrandComponent, LangSwitcherComponent, ThemeToggleComponent],
  template: `
    <header class="doc-head">
      <a class="doc-head-brand" routerLink="/docs"><app-brand /></a>

      <button class="doc-search" (click)="openSearch()">
        <span class="doc-search-icon"><app-icon name="search" /></span>
        <span class="doc-search-ph">{{ i18n.t('doc_search_placeholder') }}</span>
        <span class="doc-search-kbd">⌘K</span>
      </button>

      <div class="doc-head-right">
        <app-lang-switcher />
        <app-theme-toggle />
        @if (loggedIn) {
          <a class="doc-head-btn" routerLink="/dashboard">{{ i18n.t('doc_to_dashboard') }}</a>
        } @else {
          <a class="doc-head-signin" routerLink="/auth/login">{{ i18n.t('lp_nav_signin') }}</a>
          <a class="doc-head-btn" routerLink="/auth/register">{{ i18n.t('lp_nav_start') }}</a>
        }
        <button class="doc-head-menu" (click)="menu.emit()" [attr.aria-label]="i18n.t('lp_nav_menu')">
          <app-icon name="menu" />
        </button>
      </div>
    </header>
  `,
  styleUrl: './docs-header.component.scss',
})
export class DocsHeaderComponent {
  protected i18n = inject(I18nService);
  private token = inject(TokenService);
  private docs = inject(DocsService);

  /** Opens the sidebar drawer on mobile. */
  readonly menu = output<void>();

  protected readonly loggedIn = this.token.isLoggedIn();

  protected openSearch(): void {
    this.docs.searchOpen.set(true);
  }
}
