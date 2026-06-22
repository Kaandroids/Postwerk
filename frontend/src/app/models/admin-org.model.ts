import { Page } from './page.model';

export type { Page };

/** A platform-staff view of an organization (tenant) in the admin Organizations list. */
export interface AdminOrg {
  id: string;
  name: string;
  slug: string;
  personal: boolean;
  ownerUserId: string;
  ownerEmail: string | null;
  ownerName: string | null;
  planName: string | null;
  memberCount: number;
  createdAt: string;
  suspendedAt: string | null;
}

/** A single member row within an organization's detail view. */
export interface AdminOrgMember {
  userId: string;
  email: string;
  fullName: string;
  role: string;
  status: string;
  joinedAt: string;
}

/** Full detail for one organization, including aggregate counts and members. */
export interface AdminOrgDetail extends AdminOrg {
  planId?: string | null;
  mailboxCount: number;
  automationCount: number;
  aiCostMicrosThisMonth: number;
  suspensionReason: string | null;
  members: AdminOrgMember[];
}
