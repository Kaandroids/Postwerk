import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { I18nService } from '../../core/services/i18n.service';
import { MarketplaceService } from '../../core/services/marketplace.service';
import {
  MARKETPLACE_CATEGORIES,
  MARKETPLACE_SORTS,
  MarketplaceListing,
  MarketplaceSort,
} from '../../models/marketplace.model';
import { MarketCardComponent } from './components/market-card.component';
import { PageContentComponent } from '../dashboard/components/page-content/page-content.component';

/** Marketplace discover surface: search, sort, category rail and a compact card grid. */
@Component({
  selector: 'app-marketplace-discover',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, MarketCardComponent, PageContentComponent],
  template: `
    <app-page-content>
    <div class="mk-page" data-testid="marketplace-discover">
      <div class="dash-page-head">
        <div>
          <div class="dash-eyebrow">{{ i18n.t('mkt_discover_eyebrow') }}</div>
          <h1 class="dash-title">{{ i18n.t('mkt_discover_title') }}</h1>
          <div class="dash-subtitle">{{ i18n.t('mkt_discover_subtitle') }}</div>
        </div>
        <button class="mk-cta" data-testid="mk-publish-cta" (click)="goPublish()">
          <app-icon name="plus" /> {{ i18n.t('mkt_publish_cta') }}
        </button>
      </div>

      <div class="mk-toolbar">
        <div class="mk-search">
          <app-icon name="search" />
          <input [value]="q()" (input)="onSearch($event)" [placeholder]="i18n.t('mkt_search_placeholder')"
                 data-testid="mk-search" />
        </div>
        <div class="mk-sort">
          @for (s of sorts; track s) {
            <button [class.on]="sort() === s" (click)="setSort(s)" [attr.data-testid]="'mk-sort-' + s">
              {{ i18n.t('mkt_sort_' + s) }}
            </button>
          }
        </div>
      </div>

      <div class="mk-cats">
        <button class="mk-cat" [class.on]="cat() === null" (click)="setCat(null)" data-testid="mk-cat-all">
          {{ i18n.t('mkt_cat_all') }}
        </button>
        @for (c of categories; track c) {
          <button class="mk-cat" [class.on]="cat() === c" (click)="setCat(c)" [attr.data-testid]="'mk-cat-' + c">
            {{ i18n.t('mkt_cat_' + c) }}
          </button>
        }
      </div>

      <div class="mk-section-h">
        <h2>{{ cat() ? i18n.t('mkt_cat_' + cat()) : i18n.t('mkt_cat_all') }}</h2>
        <span class="count">{{ listings().length }}</span>
      </div>

      @if (loading()) {
        <div class="mk-loading">{{ i18n.t('mkt_loading') }}</div>
      } @else if (listings().length) {
        <div class="mk-grid" data-testid="mk-grid">
          @for (it of listings(); track it.id) {
            <app-market-card [item]="it" (open)="openDetail($event)" />
          }
        </div>
      } @else {
        <div class="mk-empty" data-testid="mk-empty">
          <div class="t">{{ i18n.t('mkt_empty_title') }}</div>
          <p class="mk-lede" style="margin:6px auto 0">{{ i18n.t('mkt_empty_text') }}</p>
        </div>
      }
    </div>
    </app-page-content>
  `,
})
export class MarketplaceDiscoverComponent {
  protected i18n = inject(I18nService);
  private market = inject(MarketplaceService);
  private router = inject(Router);

  readonly categories = MARKETPLACE_CATEGORIES;
  readonly sorts = MARKETPLACE_SORTS;

  q = signal('');
  cat = signal<string | null>(null);
  sort = signal<MarketplaceSort>('popular');
  listings = signal<MarketplaceListing[]>([]);
  loading = signal(true);

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.load();
  }

  onSearch(e: Event): void {
    this.q.set((e.target as HTMLInputElement).value);
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(), 250);
  }

  setSort(s: MarketplaceSort): void {
    this.sort.set(s);
    this.load();
  }

  setCat(c: string | null): void {
    this.cat.set(c);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.market.discover({ cat: this.cat(), sort: this.sort(), q: this.q() }).subscribe({
      next: (xs) => {
        this.listings.set(xs);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  openDetail(id: string): void {
    this.router.navigate(['/dashboard/marketplace/detail', id]);
  }

  goPublish(): void {
    this.router.navigate(['/dashboard/marketplace/publish']);
  }
}
