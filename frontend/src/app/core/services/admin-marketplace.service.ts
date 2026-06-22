import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { pageParams } from '../../shared/utils/page-params.util';
import {
  Listing,
  Review,
  ListingDetail,
  MarketplaceKpis,
  ListingFilters,
  ReviewFilters,
  Page,
} from '../../models/admin-marketplace.model';

/**
 * Platform-staff API client for Marketplace Moderation: listing + review lists (filterable +
 * paginated), KPI totals, listing detail, and the moderation mutations (take down / restore /
 * feature, hide / unhide / delete). All gated by MARKETPLACE_MODERATE + audit-logged on the backend.
 */
@Injectable({ providedIn: 'root' })
export class AdminMarketplaceService {
  private readonly api = inject(ApiService);
  private readonly base = '/admin/marketplace';

  listings(filters: ListingFilters = {}, page = 0, size = 10) {
    const params = pageParams(filters, page, size);
    return this.api.get<Page<Listing>>(`${this.base}/listings`, { params });
  }

  reviews(filters: ReviewFilters = {}, page = 0, size = 10) {
    const params = pageParams(filters, page, size);
    return this.api.get<Page<Review>>(`${this.base}/reviews`, { params });
  }

  kpis() {
    return this.api.get<MarketplaceKpis>(`${this.base}/kpis`);
  }

  getListing(id: string) {
    return this.api.get<ListingDetail>(`${this.base}/listings/${id}`);
  }

  takeDown(id: string, reason: string | null) {
    return this.api.post<Listing>(`${this.base}/listings/${id}/takedown`, { reason });
  }
  restore(id: string) { return this.api.post<Listing>(`${this.base}/listings/${id}/restore`, {}); }
  feature(id: string) { return this.api.post<Listing>(`${this.base}/listings/${id}/feature`, {}); }
  unfeature(id: string) { return this.api.post<Listing>(`${this.base}/listings/${id}/unfeature`, {}); }

  hideReview(id: string) { return this.api.post<Review>(`${this.base}/reviews/${id}/hide`, {}); }
  unhideReview(id: string) { return this.api.post<Review>(`${this.base}/reviews/${id}/unhide`, {}); }
  deleteReview(id: string, reason: string | null) {
    const q = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    return this.api.delete<void>(`${this.base}/reviews/${id}${q}`);
  }
}
