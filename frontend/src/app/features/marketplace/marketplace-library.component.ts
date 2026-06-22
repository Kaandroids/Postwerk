import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { I18nService } from '../../core/services/i18n.service';
import { MarketplaceService } from '../../core/services/marketplace.service';
import { MarketplaceAcquisition, MarketplaceLibrary, MarketplaceListing, visibilityKey, visibilityLabelKey } from '../../models/marketplace.model';
import { MarketGlyphComponent } from './components/market-glyph.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { PageContentComponent } from '../dashboard/components/page-content/page-content.component';

type LibTab = 'installed' | 'purchased' | 'published';

/** Library surface: the buyer's installed / purchased automations and authored published listings. */
@Component({
  selector: 'app-marketplace-library',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, MarketGlyphComponent, EmptyStateComponent, PageContentComponent],
  template: `
    <app-page-content>
    <div class="mk-page" data-testid="marketplace-library">
      <div class="dash-page-head">
        <div>
          <div class="dash-eyebrow">{{ i18n.t('mkt_lib_eyebrow') }}</div>
          <h1 class="dash-title">{{ i18n.t('mkt_lib_title') }}</h1>
          <div class="dash-subtitle">{{ i18n.t('mkt_lib_subtitle') }}</div>
        </div>
      </div>

      <div class="mk-lib-tabs">
        <button [class.on]="tab() === 'installed'" (click)="tab.set('installed')" data-testid="mk-tab-installed">
          <app-icon name="download" /> {{ i18n.t('mkt_lib_tab_installed') }}
          <span class="cnt">{{ lib()?.installed?.length || 0 }}</span>
        </button>
        <button [class.on]="tab() === 'purchased'" (click)="tab.set('purchased')" data-testid="mk-tab-purchased">
          <app-icon name="cart" /> {{ i18n.t('mkt_lib_tab_purchased') }}
          <span class="cnt">{{ lib()?.purchased?.length || 0 }}</span>
        </button>
        <button [class.on]="tab() === 'published'" (click)="tab.set('published')" data-testid="mk-tab-published">
          <app-icon name="upload" /> {{ i18n.t('mkt_lib_tab_published') }}
          <span class="cnt">{{ lib()?.published?.length || 0 }}</span>
        </button>
      </div>

      @if (loading()) {
        <div class="mk-loading">{{ i18n.t('mkt_loading') }}</div>
      } @else if (tab() === 'published') {
        @if (published().length) {
          <div class="mk-lib-list" data-testid="mk-lib-list">
            @for (l of published(); track l.id) {
              <div class="mk-lib-row">
                <app-market-glyph [icon]="l.icon" [color]="l.color" [size]="40" />
                <div class="main">
                  <div class="nm">
                    {{ l.name }}
                    <span class="mk-vis" [attr.data-v]="visKey(l.visibility)">
                      {{ i18n.t(visLabel(l.visibility)) }}
                    </span>
                  </div>
                  <div class="sub">{{ l.installCount }} · {{ i18n.t('mkt_installs') }}</div>
                </div>
                <span class="act" (click)="openListing(l.id)" [attr.data-testid]="'mk-manage-' + l.id">
                  <app-icon name="sliders" /> {{ i18n.t('mkt_lib_manage') }}
                </span>
                <span class="act danger" (click)="unpublish(l.id)" [attr.data-testid]="'mk-unpublish-' + l.id">
                  <app-icon name="trash" /> {{ i18n.t('mkt_lib_unpublish') }}
                </span>
              </div>
            }
          </div>
        } @else {
          <app-empty-state [title]="i18n.t('mkt_lib_empty_published')" />
        }
      } @else {
        @if (rows().length) {
          <div class="mk-lib-list" data-testid="mk-lib-list">
            @for (a of rows(); track a.id) {
              <div class="mk-lib-row">
                <app-market-glyph [icon]="a.listing.icon" [color]="a.listing.color" [size]="40" />
                <div class="main">
                  <div class="nm">{{ a.listing.name }}</div>
                  <div class="sub">
                    {{ a.installedStatus === 'ACTIVE' ? i18n.t('mkt_lib_status_active') : i18n.t('mkt_lib_status_paused') }}
                    · {{ i18n.t('mkt_by') }} {{ a.listing.author.name }}
                  </div>
                </div>
                @if (a.hidden) {
                  <span class="act" (click)="configure(a.id)" [attr.data-testid]="'mk-configure-' + a.id">
                    <app-icon name="sliders" /> {{ i18n.t('mkt_lib_configure') }}
                  </span>
                } @else {
                  <span class="act" (click)="openEditor(a.installedAutomationId)" [attr.data-testid]="'mk-open-' + a.id">
                    <app-icon name="edit" /> {{ i18n.t('mkt_lib_open') }}
                  </span>
                }
              </div>
            }
          </div>
        } @else {
          <app-empty-state
            [title]="emptyText()"
            [ctaLabel]="i18n.t('mkt_lib_browse')"
            ctaIcon="market"
            ctaTestid="mk-browse"
            (cta)="browse()" />
        }
      }
    </div>
    </app-page-content>
  `,
})
export class MarketplaceLibraryComponent {
  protected i18n = inject(I18nService);
  protected visKey = visibilityKey;
  protected visLabel = visibilityLabelKey;
  private market = inject(MarketplaceService);
  private router = inject(Router);

  lib = signal<MarketplaceLibrary | null>(null);
  tab = signal<LibTab>('installed');
  loading = signal(true);

  rows = computed<MarketplaceAcquisition[]>(() => {
    const l = this.lib();
    if (!l) return [];
    return this.tab() === 'purchased' ? l.purchased : l.installed;
  });
  published = computed<MarketplaceListing[]>(() => this.lib()?.published ?? []);
  emptyText = computed(() =>
    this.tab() === 'purchased' ? this.i18n.t('mkt_lib_empty_purchased') : this.i18n.t('mkt_lib_empty_installed')
  );

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.market.library().subscribe({
      next: (l) => {
        this.lib.set(l);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  configure(acqId: string): void {
    this.router.navigate(['/dashboard/marketplace/configure', acqId]);
  }
  openEditor(automationId: string): void {
    this.router.navigate(['/dashboard/automations', automationId, 'edit']);
  }
  openListing(id: string): void {
    this.router.navigate(['/dashboard/marketplace/detail', id]);
  }
  browse(): void {
    this.router.navigate(['/dashboard/marketplace']);
  }

  unpublish(id: string): void {
    if (!confirm(this.i18n.t('mkt_lib_unpublish_confirm'))) return;
    this.market.unpublish(id).subscribe({ next: () => this.load() });
  }
}
