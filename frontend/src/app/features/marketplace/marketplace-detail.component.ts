import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { I18nService } from '../../core/services/i18n.service';
import { MarketplaceService, pricingLabel } from '../../core/services/marketplace.service';
import { MarketplaceListingDetail, visibilityKey, visibilityLabelKey } from '../../models/marketplace.model';
import { getNodeColor, getNodeIcon } from '../../models/automation.model';
import { MarketGlyphComponent } from './components/market-glyph.component';
import { MarketStarsComponent } from './components/market-stars.component';
import { PageContentComponent } from '../dashboard/components/page-content/page-content.component';

/** Marketplace listing detail: I/O, description, content preview, reviews and a sticky buy box. */
@Component({
  selector: 'app-marketplace-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SlicePipe, IconComponent, MarketGlyphComponent, MarketStarsComponent, PageContentComponent],
  template: `
    <app-page-content>
    <div class="mk-page" data-testid="marketplace-detail">
      <button class="mk-back" (click)="back()" data-testid="mk-back">
        <app-icon name="arrowLeft" /> {{ i18n.t('mkt_detail_back') }}
      </button>

      @if (loading()) {
        <div class="mk-loading">{{ i18n.t('mkt_loading') }}</div>
      } @else if (detail(); as d) {
        <div class="mk-detail" data-layout="sidebar">
          <div>
            <div class="mk-d-head">
              <app-market-glyph [icon]="d.listing.icon" [color]="d.listing.color" [size]="60" />
              <div class="mk-d-head-text">
                <div class="mk-d-badges">
                  <span class="mk-vis" [attr.data-v]="visKey()">{{ visLabel() }}</span>
                  @if (d.listing.featured) {
                    <span class="mk-badge featured"><app-icon name="flame" /> {{ i18n.t('mkt_featured') }}</span>
                  }
                  @if (d.listing.verified) {
                    <span class="mk-badge verified"><app-icon name="verified" /> {{ i18n.t('mkt_verified') }}</span>
                  }
                  <span class="mk-cat" style="height:24px;padding:0 10px;cursor:default">
                    {{ i18n.t('mkt_cat_' + d.listing.category) }}
                  </span>
                </div>
                <h1 class="mk-d-title">{{ d.listing.name }}</h1>
                @if (d.listing.tagline) {
                  <p class="mk-d-tag">{{ d.listing.tagline }}</p>
                }
                <div class="mk-author-chip">
                  <span class="mk-ava">{{ initials(d.listing.author.name) }}</span>
                  {{ i18n.t('mkt_by') }} {{ d.listing.author.name }}
                  @if (d.listing.author.verified) { <span class="vrf"><app-icon name="verified" /></span> }
                </div>
              </div>
            </div>

            <div class="mk-d-sec">
              <h3>{{ i18n.t('mkt_detail_io') }}</h3>
              <div class="mk-io">
                <div class="mk-io-card">
                  <span class="ic"><app-icon [name]="d.listing.ioInIcon || 'inbox'" /></span>
                  <div>
                    <div class="lbl">{{ i18n.t('mkt_detail_input') }}</div>
                    <div class="val">{{ d.listing.ioInLabel || '—' }}</div>
                  </div>
                </div>
                <span class="mk-io-arrow"><app-icon name="arrowRight" /></span>
                <div class="mk-io-card out">
                  <span class="ic"><app-icon [name]="d.listing.ioOutIcon || 'send'" /></span>
                  <div>
                    <div class="lbl">{{ i18n.t('mkt_detail_output') }}</div>
                    <div class="val">{{ d.listing.ioOutLabel || '—' }}</div>
                  </div>
                </div>
              </div>
            </div>

            @if (d.listing.description) {
              <div class="mk-d-sec">
                <h3>{{ i18n.t('mkt_detail_about') }}</h3>
                <div class="mk-d-body mk-rich" [innerHTML]="d.listing.description"></div>
              </div>
            }

            <div class="mk-d-sec">
              <h3>{{ i18n.t('mkt_detail_whats_inside') }}</h3>
              @if (d.listing.visibility === 'PRIVATE') {
                <div class="mk-locked" data-testid="mk-locked">
                  <span class="ic"><app-icon name="lock" /></span>
                  <div class="t">{{ i18n.t('mkt_detail_private_locked_title') }}</div>
                  <div class="d">{{ i18n.t('mkt_detail_private_locked_text') }}</div>
                </div>
              } @else {
                <div class="mk-node-flow" data-testid="mk-node-flow">
                  @for (n of d.nodeFlow; track $index) {
                    @if ($index > 0) { <span class="sepr"><app-icon name="arrowRight" /></span> }
                    <span class="mk-node-chip">
                      <span class="g" [style.--nc]="nodeColor(n.nodeType)"><app-icon [name]="nodeIcon(n.nodeType)" /></span>
                      <span>
                        <span class="nm" style="display:block">{{ n.label || n.nodeType }}</span>
                        <span class="kd">{{ n.nodeType }}</span>
                      </span>
                    </span>
                  }
                </div>
              }
            </div>

            <div class="mk-d-sec">
              <h3>{{ i18n.t('mkt_detail_reviews') }}</h3>
              @if (d.listing.ratingCount) {
                <div class="mk-rating-summary">
                  <div class="big">{{ d.listing.ratingAvg.toFixed(1) }}</div>
                  <div>
                    <app-market-stars [rating]="d.listing.ratingAvg" />
                    <div class="sub">{{ d.listing.ratingCount }} · {{ i18n.t('mkt_detail_reviews') }}</div>
                  </div>
                </div>
              }
              @if (d.listing.owned) {
                <div class="mk-review-compose" (mouseleave)="hoverRating.set(0)" data-testid="mk-review-compose">
                  <div class="mk-rc-head">
                    <span class="mk-rc-title">{{ i18n.t('mkt_review_compose_title') }}</span>
                    <span class="mk-rc-stars">
                      @for (n of [1, 2, 3, 4, 5]; track n) {
                        <button type="button" class="mk-rc-star" [class.on]="(hoverRating() || reviewRating()) >= n"
                                (mouseenter)="hoverRating.set(n)" (click)="reviewRating.set(n)"
                                [attr.data-testid]="'mk-review-star-' + n">
                          <app-icon name="starFilled" />
                        </button>
                      }
                    </span>
                  </div>
                  <textarea class="mk-textarea mk-rc-text" [value]="reviewText()" (input)="onReviewText($event)"
                            [placeholder]="i18n.t('mkt_review_placeholder')" data-testid="mk-review-text"></textarea>
                  <div class="mk-rc-foot">
                    <span class="mk-rc-hint">
                      {{ reviewRating() ? reviewRating() + '/5' : i18n.t('mkt_review_pick_stars') }}
                    </span>
                    <button class="mk-cta accent" (click)="submitReview()"
                            [disabled]="reviewSaving() || !reviewRating() || !reviewText().trim()"
                            data-testid="mk-review-submit">
                      <app-icon name="send" /> {{ i18n.t('mkt_review_submit') }}
                    </button>
                  </div>
                </div>
              }

              @if (reviews().length) {
                <div class="mk-reviews">
                  @for (r of reviews(); track r.id) {
                    <div class="mk-review" [class.mine]="r.mine">
                      <div class="mk-review-top">
                        <span class="who">{{ r.userName }}</span>
                        @if (r.mine) { <span class="mk-review-badge">{{ i18n.t('mkt_review_mine') }}</span> }
                        <app-market-stars [rating]="r.rating" />
                        <span class="when">{{ r.createdAt | slice: 0:10 }}</span>
                      </div>
                      @if (r.text) { <p>{{ r.text }}</p> }
                    </div>
                  }
                </div>
              } @else {
                <div class="mk-d-body">{{ i18n.t('mkt_detail_no_reviews') }}</div>
              }
            </div>
          </div>

          <div>
            <div class="mk-buybox" data-testid="mk-buybox">
              <div class="price-row">{{ price() }}</div>
              @if (d.listing.owned) {
                @if (d.listing.visibility === 'PRIVATE') {
                  <button class="mk-cta accent block" (click)="goConfigure()" data-testid="mk-configure">
                    <app-icon name="sliders" /> {{ i18n.t('mkt_detail_configure') }}
                  </button>
                } @else {
                  <button class="mk-cta accent block" (click)="goLibrary()" data-testid="mk-open">
                    <app-icon name="checkCircle" /> {{ i18n.t('mkt_detail_installed') }}
                  </button>
                }
              } @else {
                <button class="mk-cta accent block" (click)="confirmOpen.set(true)"
                        [disabled]="installing()" data-testid="mk-install">
                  <app-icon name="download" />
                  {{ installing() ? i18n.t('mkt_detail_installing') : i18n.t('mkt_detail_install') }}
                </button>
              }

              <div class="facts">
                <div class="mk-fact">
                  <span class="k"><app-icon name="workflow" /> {{ i18n.t('mkt_nodes') }}</span>
                  <span class="v">{{ d.listing.nodeCount }}</span>
                </div>
                <div class="mk-fact">
                  <span class="k"><app-icon name="download" /> {{ i18n.t('mkt_installs') }}</span>
                  <span class="v">{{ d.listing.installCount }}</span>
                </div>
                <div class="mk-fact">
                  <span class="k"><app-icon name="tag" /> {{ i18n.t('mkt_detail_category') }}</span>
                  <span class="v">{{ i18n.t('mkt_cat_' + d.listing.category) }}</span>
                </div>
                @if (d.listing.version) {
                  <div class="mk-fact">
                    <span class="k"><app-icon name="info" /> {{ i18n.t('mkt_detail_version') }}</span>
                    <span class="v">{{ d.listing.version }}</span>
                  </div>
                }
              </div>
            </div>

            <div class="mk-d-sec" style="margin-top:18px">
              <h3>{{ i18n.t('mkt_detail_publisher') }}</h3>
              <div class="mk-authorcard">
                <span class="mk-ava">{{ initials(d.listing.author.name) }}</span>
                <div>
                  <div class="nm">
                    {{ d.listing.author.name }}
                    @if (d.listing.author.verified) { <span class="vrf"><app-icon name="verified" /></span> }
                  </div>
                  <div class="hd">{{ d.listing.author.listingCount }} · {{ i18n.t('mkt_nodes') }}</div>
                </div>
                <div class="stats">
                  <div>
                    <div class="n">{{ d.listing.author.installCount }}</div>
                    <div class="l">{{ i18n.t('mkt_installs') }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        @if (confirmOpen()) {
          <div class="mk-scrim" (click)="confirmOpen.set(false)">
            <div class="mk-modal" (click)="$event.stopPropagation()" data-testid="mk-confirm">
              <div class="mk-modal-top">
                <app-market-glyph [icon]="d.listing.icon" [color]="d.listing.color" [size]="48" />
                <h3>{{ i18n.t('mkt_confirm_title') }}</h3>
                <p>{{ i18n.t('mkt_confirm_text') }}</p>
              </div>
              <div class="price-line">
                <span>{{ d.listing.name }}</span>
                <span>{{ price() }}</span>
              </div>
              <div class="mk-modal-foot">
                <button class="mk-cta ghost" (click)="confirmOpen.set(false)" data-testid="mk-confirm-cancel">
                  {{ i18n.t('mkt_confirm_cancel') }}
                </button>
                <button class="mk-cta accent" (click)="install()" [disabled]="installing()"
                        data-testid="mk-confirm-install">
                  {{ i18n.t('mkt_confirm_install') }}
                </button>
              </div>
            </div>
          </div>
        }
      } @else {
        <div class="mk-error-box">{{ i18n.t('mkt_error') }}</div>
      }
    </div>
    </app-page-content>
  `,
})
export class MarketplaceDetailComponent {
  protected i18n = inject(I18nService);
  private market = inject(MarketplaceService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  private id = this.route.snapshot.paramMap.get('id') ?? '';
  detail = signal<MarketplaceListingDetail | null>(null);
  loading = signal(true);
  installing = signal(false);
  confirmOpen = signal(false);
  reviewRating = signal(0);
  hoverRating = signal(0);
  reviewText = signal('');
  reviewSaving = signal(false);

  readonly nodeColor = getNodeColor;
  readonly nodeIcon = getNodeIcon;

  /** Reviews with the requesting user's own review surfaced first. */
  reviews = computed(() => {
    const list = this.detail()?.reviews ?? [];
    return [...list].sort((a, b) => Number(b.mine) - Number(a.mine));
  });

  price = computed(() => {
    const l = this.detail()?.listing;
    return l ? pricingLabel(l.pricingModel, l.price, (k) => this.i18n.t(k)) : '';
  });
  visKey = computed(() => visibilityKey(this.detail()?.listing.visibility ?? 'PUBLIC'));
  visLabel = computed(() => this.i18n.t(visibilityLabelKey(this.detail()?.listing.visibility ?? 'PUBLIC')));

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.market.getDetail(this.id).subscribe({
      next: (d) => {
        this.detail.set(d);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onReviewText(e: Event): void {
    this.reviewText.set((e.target as HTMLTextAreaElement).value);
  }

  initials(name: string): string {
    return name.trim().slice(0, 2).toUpperCase();
  }

  install(): void {
    if (this.installing()) return;
    this.installing.set(true);
    this.market.install(this.id).subscribe({
      next: (acq) => {
        this.installing.set(false);
        this.confirmOpen.set(false);
        if (acq.hidden) {
          this.router.navigate(['/dashboard/marketplace/configure', acq.id]);
        } else {
          this.router.navigate(['/dashboard/automations', acq.installedAutomationId, 'edit']);
        }
      },
      error: () => {
        this.installing.set(false);
        this.confirmOpen.set(false);
      },
    });
  }

  submitReview(): void {
    if (this.reviewSaving()) return;
    this.reviewSaving.set(true);
    this.market.addReview(this.id, { rating: this.reviewRating(), text: this.reviewText() || null }).subscribe({
      next: () => {
        this.reviewSaving.set(false);
        this.reviewText.set('');
        this.reviewRating.set(0);
        this.hoverRating.set(0);
        this.load();
      },
      error: () => this.reviewSaving.set(false),
    });
  }

  goConfigure(): void {
    this.router.navigate(['/dashboard/marketplace-library']);
  }

  goLibrary(): void {
    this.router.navigate(['/dashboard/marketplace-library']);
  }

  back(): void {
    this.router.navigate(['/dashboard/marketplace']);
  }
}
