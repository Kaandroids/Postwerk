import { Page } from './page.model';

export type { Page };

export type AnnouncementType = 'INFO' | 'SUCCESS' | 'WARNING' | 'MAINTENANCE';
export type AnnouncementPlacement = 'BANNER' | 'TOAST' | 'LOGIN';
export type AnnouncementAudience = 'EVERYONE' | 'PLAN' | 'ORG' | 'STAFF';
export type AnnouncementLifecycle = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
/** Derived display status. */
export type AnnouncementStatus = 'DRAFT' | 'SCHEDULED' | 'LIVE' | 'EXPIRED' | 'ARCHIVED';

export interface Announcement {
  id: string;
  titleDe: string;
  titleEn: string;
  bodyDe: string | null;
  bodyEn: string | null;
  ctaLabelDe: string | null;
  ctaLabelEn: string | null;
  ctaUrl: string | null;
  type: AnnouncementType;
  placement: AnnouncementPlacement;
  audience: AnnouncementAudience;
  audiencePlans: string[];
  audienceOrgId: string | null;
  audienceOrgName: string | null;
  dismissible: boolean;
  lifecycle: AnnouncementLifecycle;
  status: AnnouncementStatus;
  startsAt: string | null;
  endsAt: string | null;
  createdByName: string | null;
  updatedByName: string | null;
  updatedAt: string;
}

export interface AnnouncementHistoryEntry {
  label: string;
  actor: string;
  at: string;
}

export interface AnnouncementDetail {
  announcement: Announcement;
  history: AnnouncementHistoryEntry[];
}

export interface AnnouncementKpis {
  live: number;
  scheduled: number;
  drafts: number;
  maintenanceLive: number;
  expired30d: number;
  nextLiveAt: string | null;
}

export interface AnnouncementFilters {
  search?: string;
  type?: '' | AnnouncementType;
  status?: '' | AnnouncementStatus;
  audience?: '' | AnnouncementAudience;
  placement?: '' | AnnouncementPlacement;
  sort?: '' | 'window' | 'updated' | 'status';
}

/** Create/update payload — the editor saves the whole draft. */
export interface AnnouncementRequest {
  titleDe: string;
  titleEn: string;
  bodyDe: string;
  bodyEn: string;
  ctaLabelDe: string;
  ctaLabelEn: string;
  ctaUrl: string;
  type: AnnouncementType;
  placement: AnnouncementPlacement;
  audience: AnnouncementAudience;
  audiencePlans: string[];
  audienceOrgId: string | null;
  dismissible: boolean;
  startsAt: string | null;
  endsAt: string | null;
}
