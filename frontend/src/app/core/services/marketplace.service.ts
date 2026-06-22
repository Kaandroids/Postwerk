import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { ApiService } from './api.service';
import {
  AutomationConstantInput,
  MarketplaceAcquisition,
  MarketplaceLibrary,
  MarketplaceListing,
  MarketplaceListingDetail,
  MarketplaceSort,
  PricingModel,
  PublishListingRequest,
  Review,
  ReviewRequest,
} from '../../models/marketplace.model';

/**
 * Client for the automation marketplace: discovery, listing detail, publishing,
 * installing (buyer-owned copies), the buyer library, configuring installs, and reviews.
 */
@Injectable({ providedIn: 'root' })
export class MarketplaceService {
  private readonly api = inject(ApiService);
  private readonly basePath = '/marketplace';

  /** Discover published listings, optionally filtered by category/search and sorted. */
  discover(opts: { cat?: string | null; sort?: MarketplaceSort; q?: string | null } = {}) {
    let params = new HttpParams();
    if (opts.cat) params = params.set('cat', opts.cat);
    if (opts.sort) params = params.set('sort', opts.sort);
    if (opts.q) params = params.set('q', opts.q);
    return this.api.get<MarketplaceListing[]>(`${this.basePath}/listings`, { params });
  }

  /** Full detail of a listing (reviews + public node-flow or private publishable constants). */
  getDetail(id: string) {
    return this.api.get<MarketplaceListingDetail>(`${this.basePath}/listings/${id}`);
  }

  /** Publish one of the author's automations as a listing. */
  publish(request: PublishListingRequest) {
    return this.api.post<MarketplaceListingDetail>(`${this.basePath}/listings`, request);
  }

  /** Install a listing — creates a buyer-owned copy + entitlement. */
  install(id: string) {
    return this.api.post<MarketplaceAcquisition>(`${this.basePath}/listings/${id}/install`, {});
  }

  /** Unpublish a listing owned by the author. */
  unpublish(id: string) {
    return this.api.delete<void>(`${this.basePath}/listings/${id}`);
  }

  /** The buyer's library: installed / purchased / published. */
  library() {
    return this.api.get<MarketplaceLibrary>(`${this.basePath}/library`);
  }

  /** Save buyer-overridable constant values for an installed (hidden) automation. */
  saveConstants(acquisitionId: string, constants: AutomationConstantInput[]) {
    return this.api.put<void>(`${this.basePath}/acquisitions/${acquisitionId}/constants`, { constants });
  }

  /** Bind the buyer's email accounts to the installed automation's trigger. */
  bindAccounts(acquisitionId: string, accountIds: string[]) {
    return this.api.put<void>(`${this.basePath}/acquisitions/${acquisitionId}/accounts`, { accountIds });
  }

  /** Activate the installed automation. */
  activate(acquisitionId: string) {
    return this.api.post<MarketplaceAcquisition>(`${this.basePath}/acquisitions/${acquisitionId}/activate`, {});
  }

  /** List reviews for a listing. */
  reviews(id: string) {
    return this.api.get<Review[]>(`${this.basePath}/listings/${id}/reviews`);
  }

  /** Create or update the requesting user's review of a listing. */
  addReview(id: string, request: ReviewRequest) {
    return this.api.post<Review>(`${this.basePath}/listings/${id}/reviews`, request);
  }
}

/** i18n keys for pricing-model labels (resolve via I18nService.t). */
export const PRICING_LABEL_KEYS: Record<PricingModel, string> = {
  FREE: 'mkt_price_free',
  ONE_TIME: 'mkt_price_one_time',
  MONTHLY: 'mkt_price_monthly',
  YEARLY: 'mkt_price_yearly',
  FREEMIUM: 'mkt_price_freemium',
};

/**
 * Builds a human price label. FREE/FREEMIUM use their i18n label; paid models render the
 * price with a per-period suffix.
 *
 * @param t a translation function (e.g. `I18nService.t`)
 */
export function pricingLabel(
  model: PricingModel,
  price: number | null | undefined,
  t: (key: string) => string
): string {
  if (model === 'FREE') return t('mkt_price_free');
  if (model === 'FREEMIUM') return t('mkt_price_freemium');
  const amount = price != null ? `€${price}` : t(PRICING_LABEL_KEYS[model]);
  if (model === 'MONTHLY') return `${amount} ${t('mkt_per_month')}`;
  if (model === 'YEARLY') return `${amount} ${t('mkt_per_year')}`;
  return amount;
}
