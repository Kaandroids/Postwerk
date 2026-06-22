import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { I18nService } from '../../core/services/i18n.service';
import { MarketplaceService, pricingLabel } from '../../core/services/marketplace.service';
import { AutomationService } from '../../core/services/automation.service';
import { Automation, AutomationConstant } from '../../models/automation.model';
import {
  ListingVisibility,
  MARKETPLACE_CATEGORIES,
  MarketplaceListing,
  PRICING_MODELS,
  PricingModel,
  PublishableConstant,
} from '../../models/marketplace.model';
import { MarketCardComponent } from './components/market-card.component';
import { MkRichTextComponent } from './components/mk-rich-text.component';
import { PageContentComponent } from '../dashboard/components/page-content/page-content.component';

/** Publish surface: turn one of the author's automations into a marketplace listing. */
@Component({
  selector: 'app-marketplace-publish',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, MarketCardComponent, MkRichTextComponent, PageContentComponent],
  template: `
    <app-page-content>
    <div class="mk-page" data-testid="marketplace-publish">
      <button class="mk-back" (click)="back()" data-testid="mk-back">
        <app-icon name="arrowLeft" /> {{ i18n.t('mkt_detail_back') }}
      </button>
      <div class="mk-eyebrow">{{ i18n.t('mkt_publish_eyebrow') }}</div>
      <h1 class="mk-h1">{{ i18n.t('mkt_publish_title') }}</h1>
      <p class="mk-lede">{{ i18n.t('mkt_publish_subtitle') }}</p>

      <div class="mk-publish-grid">
        <div>
          <div class="mk-field">
            <div class="mk-label">{{ i18n.t('mkt_publish_source') }}
              <span class="aux">{{ i18n.t('mkt_publish_source_hint') }}</span>
            </div>
            <select class="mk-select" [value]="automationId()" (change)="onSource($event)" data-testid="mk-source">
              <option value="">{{ i18n.t('mkt_publish_select_automation') }}</option>
              @for (a of automations(); track a.id) {
                <option [value]="a.id">{{ a.name }}</option>
              }
            </select>
            @if (err().automation) { <div class="mk-field-error">{{ i18n.t('mkt_publish_err_automation') }}</div> }
          </div>

          <div class="mk-field">
            <div class="mk-label">{{ i18n.t('mkt_publish_name') }}</div>
            <input class="mk-input" [value]="name()" (input)="set('name', $event)" data-testid="mk-name" />
            @if (err().name) { <div class="mk-field-error">{{ i18n.t('mkt_publish_err_name') }}</div> }
          </div>

          <div class="mk-field">
            <div class="mk-label">{{ i18n.t('mkt_publish_tagline') }}</div>
            <input class="mk-input" [value]="tagline()" (input)="set('tagline', $event)" data-testid="mk-tagline" />
            @if (err().tagline) { <div class="mk-field-error">{{ i18n.t('mkt_publish_err_tagline') }}</div> }
          </div>

          <div class="mk-field">
            <div class="mk-label">{{ i18n.t('mkt_publish_description') }}
              <span class="aux">{{ i18n.t('mkt_publish_description_hint') }}</span>
            </div>
            <app-mk-rich-text [value]="description()" (valueChange)="description.set($event)"
                              [placeholder]="i18n.t('mkt_publish_description_placeholder')" />
          </div>

          <div class="mk-field">
            <div class="mk-label">{{ i18n.t('mkt_publish_category') }}</div>
            <select class="mk-select" [value]="category()" (change)="onCategory($event)" data-testid="mk-category">
              <option value="">—</option>
              @for (c of categories; track c) {
                <option [value]="c">{{ i18n.t('mkt_cat_' + c) }}</option>
              }
            </select>
            @if (err().category) { <div class="mk-field-error">{{ i18n.t('mkt_publish_err_category') }}</div> }
          </div>

          <div class="mk-field">
            <div class="mk-label">{{ i18n.t('mkt_publish_visibility') }}</div>
            <div class="mk-choice">
              <button class="mk-choice-card" [class.on]="visibility() === 'PUBLIC'" (click)="visibility.set('PUBLIC')"
                      data-testid="mk-vis-public">
                <span class="tick"><app-icon name="check" /></span>
                <span class="ic pub"><app-icon name="globe" /></span>
                <span class="ti">{{ i18n.t('mkt_publish_visibility_public_title') }}</span>
                <span class="de">{{ i18n.t('mkt_publish_visibility_public_text') }}</span>
              </button>
              <button class="mk-choice-card" [class.on]="visibility() === 'PRIVATE'" (click)="visibility.set('PRIVATE')"
                      data-testid="mk-vis-private">
                <span class="tick"><app-icon name="check" /></span>
                <span class="ic prv"><app-icon name="lock" /></span>
                <span class="ti">{{ i18n.t('mkt_publish_visibility_private_title') }}</span>
                <span class="de">{{ i18n.t('mkt_publish_visibility_private_text') }}</span>
              </button>
            </div>
          </div>

          <div class="mk-field">
            <div class="mk-label">{{ i18n.t('mkt_publish_pricing') }}</div>
            <div class="mk-pricing-grid">
              @for (pm of pricingModels; track pm) {
                <button class="mk-pm" [class.on]="pricingModel() === pm" (click)="pricingModel.set(pm)"
                        [attr.data-testid]="'mk-pm-' + pm">
                  <div class="t">{{ i18n.t('mkt_price_' + pmKey(pm)) }}</div>
                </button>
              }
            </div>
          </div>

          @if (isPaid()) {
            <div class="mk-field">
              <div class="mk-label">{{ i18n.t('mkt_publish_price') }} <span class="aux">€</span></div>
              <input class="mk-input" type="number" min="0" [value]="price()" (input)="set('price', $event)"
                     data-testid="mk-price" />
            </div>
          }

          <div class="mk-field">
            <div class="mk-label">{{ i18n.t('mkt_publish_share_kb') }}
              <span class="aux">{{ i18n.t('mkt_publish_share_kb_hint') }}</span>
            </div>
            <div class="mk-pc">
              <div class="mk-pc-row" [class.on]="shareKbEntries()">
                <span class="box" (click)="shareKbEntries.set(!shareKbEntries())" data-testid="mk-share-kb">
                  @if (shareKbEntries()) { <app-icon name="check" /> }
                </span>
                <span class="key">{{ i18n.t('mkt_publish_share_kb_toggle') }}</span>
              </div>
            </div>
          </div>

          @if (visibility() === 'PRIVATE') {
            <div class="mk-field">
              <div class="mk-label">{{ i18n.t('mkt_publish_constants_title') }}
                <span class="aux">{{ i18n.t('mkt_publish_constants_hint') }}</span>
              </div>
              <div class="mk-pc" data-testid="mk-pc">
                @for (c of constants(); track c.name) {
                  <div class="mk-pc-row" [class.on]="isPicked(c.name)">
                    <span class="box" (click)="togglePick(c.name)" [attr.data-testid]="'mk-pc-box-' + c.name">
                      @if (isPicked(c.name)) { <app-icon name="check" /> }
                    </span>
                    <span class="key">{{ c.name }}</span>
                    <input class="desc" [value]="descOf(c.name)" (input)="setDesc(c.name, $event)"
                           [disabled]="!isPicked(c.name)"
                           [placeholder]="i18n.t('mkt_publish_constant_desc_placeholder')" />
                  </div>
                }
              </div>
            </div>
          }

          <div class="mk-publish-foot">
            <span class="sp"></span>
            <button class="mk-cta accent" (click)="submit()" [disabled]="saving()" data-testid="mk-submit">
              <app-icon name="upload" /> {{ i18n.t('mkt_publish_submit') }}
            </button>
          </div>
        </div>

        <div>
          <div class="mk-label" style="margin-bottom:10px">{{ i18n.t('mkt_publish_preview') }}</div>
          <app-market-card [item]="preview()" />
          @if (descHasContent()) {
            <div class="mk-desc-preview" data-testid="mk-desc-preview">
              <div class="mk-desc-preview-title">{{ i18n.t('mkt_publish_description_preview') }}</div>
              <div class="mk-d-body mk-rich" [innerHTML]="description()"></div>
            </div>
          }
        </div>
      </div>
    </div>
    </app-page-content>
  `,
})
export class MarketplacePublishComponent {
  protected i18n = inject(I18nService);
  private market = inject(MarketplaceService);
  private automationSvc = inject(AutomationService);
  private router = inject(Router);

  readonly categories = MARKETPLACE_CATEGORIES;
  readonly pricingModels = PRICING_MODELS;

  automations = signal<Automation[]>([]);
  automationId = signal('');
  name = signal('');
  tagline = signal('');
  description = signal('');
  category = signal('');
  visibility = signal<ListingVisibility>('PUBLIC');
  pricingModel = signal<PricingModel>('FREE');
  price = signal('');
  shareKbEntries = signal(false);
  constants = signal<AutomationConstant[]>([]);
  picked = signal<Record<string, { on: boolean; description: string }>>({});
  saving = signal(false);
  err = signal<{ automation?: boolean; name?: boolean; tagline?: boolean; category?: boolean }>({});

  isPaid = computed(() => this.pricingModel() === 'ONE_TIME' || this.pricingModel() === 'MONTHLY' || this.pricingModel() === 'YEARLY');

  /** True when the rich-text description has visible content (ignores empty markup). */
  descHasContent = computed(() => this.description().replace(/<[^>]*>/g, '').trim().length > 0
    || /<img\b/i.test(this.description()));

  preview = computed<MarketplaceListing>(() => ({
    id: 'preview',
    name: this.name() || this.i18n.t('mkt_publish_name'),
    tagline: this.tagline() || this.i18n.t('mkt_publish_tagline'),
    description: this.description() || null,
    category: this.category() || 'productivity',
    kind: 'AUTOMATION',
    visibility: this.visibility(),
    pricingModel: this.pricingModel(),
    price: this.price() ? Number(this.price()) : null,
    version: '1.0.0',
    icon: 'cube',
    color: null,
    ioInIcon: null, ioInLabel: null, ioOutIcon: null, ioOutLabel: null,
    nodeCount: 0,
    constantCount: this.constants().length,
    ratingAvg: 0,
    ratingCount: 0,
    installCount: 0,
    featured: false,
    verified: false,
    status: 'PUBLISHED',
    author: { id: '', name: '—', verified: false, listingCount: 0, installCount: 0 },
    owned: false,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }));

  constructor() {
    this.automationSvc.list().subscribe((xs) => this.automations.set(xs));
  }

  pmKey(pm: PricingModel): string {
    return pm.toLowerCase();
  }

  set(field: 'name' | 'tagline' | 'price', e: Event): void {
    const v = (e.target as HTMLInputElement).value;
    ({ name: this.name, tagline: this.tagline, price: this.price })[field].set(v);
  }

  onSource(e: Event): void {
    const id = (e.target as HTMLSelectElement).value;
    this.automationId.set(id);
    const auto = this.automations().find((a) => a.id === id);
    if (auto && !this.name()) this.name.set(auto.name);
    this.constants.set([]);
    this.picked.set({});
    if (id) {
      this.automationSvc.get(id).subscribe((d) => this.constants.set(d.constants));
    }
  }

  onCategory(e: Event): void {
    this.category.set((e.target as HTMLSelectElement).value);
  }

  isPicked(name: string): boolean {
    return !!this.picked()[name]?.on;
  }
  descOf(name: string): string {
    return this.picked()[name]?.description ?? '';
  }
  togglePick(name: string): void {
    const cur = this.picked();
    const entry = cur[name] ?? { on: false, description: '' };
    this.picked.set({ ...cur, [name]: { ...entry, on: !entry.on } });
  }
  setDesc(name: string, e: Event): void {
    const cur = this.picked();
    const entry = cur[name] ?? { on: true, description: '' };
    this.picked.set({ ...cur, [name]: { ...entry, description: (e.target as HTMLInputElement).value } });
  }

  private publishableConstants(): PublishableConstant[] {
    const p = this.picked();
    return this.constants()
      .filter((c) => p[c.name]?.on)
      .map((c) => ({ name: c.name, description: p[c.name].description || null }));
  }

  submit(): void {
    const e = {
      automation: !this.automationId(),
      name: !this.name().trim(),
      tagline: !this.tagline().trim(),
      category: !this.category(),
    };
    this.err.set(e);
    if (e.automation || e.name || e.tagline || e.category) return;

    this.saving.set(true);
    this.market
      .publish({
        automationId: this.automationId(),
        name: this.name().trim(),
        tagline: this.tagline().trim(),
        description: this.description() || null,
        category: this.category(),
        visibility: this.visibility(),
        pricingModel: this.pricingModel(),
        price: this.isPaid() && this.price() ? Number(this.price()) : null,
        publishableConstants: this.visibility() === 'PRIVATE' ? this.publishableConstants() : undefined,
        shareKbEntries: this.shareKbEntries(),
      })
      .subscribe({
        next: (d) => {
          this.saving.set(false);
          this.router.navigate(['/dashboard/marketplace/detail', d.listing.id]);
        },
        error: () => this.saving.set(false),
      });
  }

  back(): void {
    this.router.navigate(['/dashboard/marketplace']);
  }
}
