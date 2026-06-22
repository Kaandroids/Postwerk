import { Page } from './page.model';

export type { Page };

/** Staff-facing moderation status of a listing. */
export type ListingModStatus = 'PUBLIC' | 'PRIVATE' | 'PAUSED' | 'TAKEN_DOWN';

/** A marketplace listing row (admin Marketplace Moderation). */
export interface Listing {
  id: string;
  name: string;
  slug: string;
  authorName: string | null;
  authorEmail: string | null;
  kind: string;            // AUTOMATION | INTEGRATION
  pricingModel: string;    // FREE | ONE_TIME | MONTHLY | YEARLY | FREEMIUM
  price: number;
  status: ListingModStatus;
  featured: boolean;
  takenDown: boolean;
  installCount: number;
  ratingAvg: number;
  ratingCount: number;
  category: string | null;
  createdAt: string;
}

/** A marketplace review row. */
export interface Review {
  id: string;
  listingId: string;
  listingName: string | null;
  authorName: string | null;
  authorEmail: string | null;
  rating: number;
  text: string | null;
  hidden: boolean;
  createdAt: string;
}

/** A listing + its reviews (incl. hidden, for staff) — detail modal. */
export interface ListingDetail {
  listing: Listing;
  description: string | null;
  reviews: Review[];
}

/** KPI strip totals for both segments. */
export interface MarketplaceKpis {
  totalListings: number;
  publishedListings: number;
  pausedListings: number;
  takenDownListings: number;
  totalInstalls: number;
  avgRating: number;
  pendingListings: number;
  totalReviews: number;
  visibleReviews: number;
  hiddenReviews: number;
  reviewAvgRating: number;
  lowRatings: number;
  pendingReviews: number;
}

export interface ListingFilters {
  search?: string;
  status?: '' | ListingModStatus;
  kind?: '' | 'AUTOMATION' | 'INTEGRATION';
  pricing?: '' | 'FREE' | 'PAID';
  sort?: '' | 'newest' | 'installs' | 'rating';
}

export interface ReviewFilters {
  search?: string;
  rating?: number;
  status?: '' | 'VISIBLE' | 'HIDDEN';
  sort?: '' | 'newest' | 'lowest';
}
