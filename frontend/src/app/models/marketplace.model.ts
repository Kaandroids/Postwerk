import { AutomationConstant, AutomationConstantInput, AutomationKind } from './automation.model';

/** Listing visibility — PUBLIC (fully editable copy) or PRIVATE (content-hidden copy). */
export type ListingVisibility = 'PUBLIC' | 'PRIVATE';

/** Short data-attribute key for a listing's visibility ('private' | 'public'). */
export function visibilityKey(v: ListingVisibility): 'private' | 'public' {
  return v === 'PRIVATE' ? 'private' : 'public';
}

/** i18n key for a listing's visibility title. */
export function visibilityLabelKey(v: ListingVisibility): string {
  return v === 'PRIVATE' ? 'mkt_publish_visibility_private_title' : 'mkt_publish_visibility_public_title';
}

/** Pricing model of a listing (metadata-only — no charge is taken). */
export type PricingModel = 'FREE' | 'ONE_TIME' | 'MONTHLY' | 'YEARLY' | 'FREEMIUM';

/** Publish status of a listing. */
export type ListingStatus = 'PUBLISHED' | 'UNPUBLISHED';

/** Acquisition (entitlement) status. */
export type AcquisitionStatus = 'ACTIVE' | 'REMOVED';

/** Lightweight public summary of a listing author. */
export interface AuthorSummary {
  id: string;
  name: string;
  verified: boolean;
  listingCount: number;
  installCount: number;
}

/** Card + base detail representation of a marketplace listing. */
export interface MarketplaceListing {
  id: string;
  name: string;
  tagline: string | null;
  description: string | null;
  category: string;
  /** Whether the listing publishes an AUTOMATION or an INTEGRATION (reusable function). */
  kind: AutomationKind;
  visibility: ListingVisibility;
  pricingModel: PricingModel;
  price: number | null;
  version: string | null;
  icon: string | null;
  color: string | null;
  ioInIcon: string | null;
  ioInLabel: string | null;
  ioOutIcon: string | null;
  ioOutLabel: string | null;
  nodeCount: number;
  constantCount: number;
  ratingAvg: number;
  ratingCount: number;
  installCount: number;
  featured: boolean;
  verified: boolean;
  status: ListingStatus;
  author: AuthorSummary;
  /** Whether the requesting user already has an active acquisition of this listing. */
  owned: boolean;
  createdAt: string;
  updatedAt: string;
}

/** A single node chip in a PUBLIC listing's node-flow preview. */
export interface NodeChip {
  nodeType: string;
  label: string | null;
}

/** Author-declared metadata for a buyer-overridable constant of a PRIVATE listing. */
export interface PublishableConstant {
  name: string;
  description: string | null;
  /** The constant value type; response-only. */
  type?: AutomationConstant['type'];
}

/** Full detail of a listing: base + reviews + (PUBLIC) node flow or (PRIVATE) publishable constants. */
export interface MarketplaceListingDetail {
  listing: MarketplaceListing;
  nodeFlow: NodeChip[];
  publishableConstants: PublishableConstant[];
  reviews: Review[];
}

/** Request to publish one of the author's automations to the marketplace. */
export interface PublishListingRequest {
  automationId: string;
  name: string;
  tagline?: string | null;
  description?: string | null;
  category: string;
  visibility: ListingVisibility;
  pricingModel: PricingModel;
  price?: number | null;
  version?: string | null;
  icon?: string | null;
  color?: string | null;
  ioInIcon?: string | null;
  ioInLabel?: string | null;
  ioOutIcon?: string | null;
  ioOutLabel?: string | null;
  /** For PRIVATE listings: the subset of constants buyers may override. */
  publishableConstants?: PublishableConstant[];
  /** PUBLIC listings: opt-in to ship referenced knowledge-base entries to installers (else schema only). */
  shareKbEntries?: boolean | null;
}

/** A buyer's acquisition (entitlement) joined with its listing summary and installed-copy state. */
export interface MarketplaceAcquisition {
  id: string;
  listingId: string;
  installedAutomationId: string;
  pricingModel: PricingModel;
  price: number | null;
  status: AcquisitionStatus;
  /** Whether the installed copy is content-hidden (PRIVATE listing). */
  hidden: boolean;
  /** Status of the installed automation (PAUSED/ACTIVE/...). */
  installedStatus: string;
  listing: MarketplaceListing;
  createdAt: string;
}

/** A single review on a listing, including the reviewer's display name. */
export interface Review {
  id: string;
  userId: string;
  userName: string;
  rating: number;
  text: string | null;
  createdAt: string;
  /** Whether this review was written by the requesting user (shows the "Deine" badge). */
  mine: boolean;
}

/** Request to create or update the requesting user's review of a listing. */
export interface ReviewRequest {
  rating: number;
  text?: string | null;
}

/** The buyer's marketplace library, split into the three Library tabs. */
export interface MarketplaceLibrary {
  installed: MarketplaceAcquisition[];
  purchased: MarketplaceAcquisition[];
  published: MarketplaceListing[];
}

/** Re-export for the value-only constants editor used in Configure. */
export type { AutomationConstant, AutomationConstantInput };

/** Marketplace category keys (mirrors the discover category rail). */
export const MARKETPLACE_CATEGORIES = [
  'sales',
  'support',
  'crm',
  'marketing',
  'finance',
  'productivity',
] as const;
export type MarketplaceCategory = (typeof MARKETPLACE_CATEGORIES)[number];

/** Discover sort options. */
export const MARKETPLACE_SORTS = ['popular', 'new', 'installs', 'rating'] as const;
export type MarketplaceSort = (typeof MARKETPLACE_SORTS)[number];

/** All selectable pricing models for the publish surface. */
export const PRICING_MODELS: PricingModel[] = ['FREE', 'ONE_TIME', 'MONTHLY', 'YEARLY', 'FREEMIUM'];
