/**
 * Organization role within the multi-tenant model (#4). A strict ladder — each role grants everything
 * the one below it does, plus more: VIEWER ⊂ MEMBER ⊂ EDITOR ⊂ ADMIN ⊂ OWNER.
 * Editor builds & tests but cannot go live; Admin/Owner activate; Member operates; Viewer observes.
 */
export type OrgRole = 'OWNER' | 'ADMIN' | 'EDITOR' | 'MEMBER' | 'VIEWER';

/** Lifecycle state of a membership. */
export type MembershipStatus = 'INVITED' | 'ACTIVE' | 'SUSPENDED';

/**
 * Fine-grained org capability — mirrors the backend {@code Permission} enum (#4). The UI uses these
 * (via {@code OrganizationService.can()}) to hide nav/actions a role cannot use; the backend remains
 * the source of truth and re-checks every mutation.
 */
export type OrgPermission =
  | 'MAILBOX_CONNECT' | 'MAILBOX_READ' | 'MAILBOX_SEND' | 'MAILBOX_FOLDERS'
  | 'AUTOMATION_VIEW' | 'AUTOMATION_EDIT' | 'AUTOMATION_ACTIVATE' | 'AUTOMATION_TEST'
  | 'APPROVAL_VIEW' | 'APPROVAL_DECIDE'
  | 'RESOURCE_VIEW' | 'RESOURCE_EDIT' | 'SECRET_MANAGE'
  | 'MARKETPLACE_PUBLISH' | 'MARKETPLACE_INSTALL'
  | 'MEMBER_INVITE' | 'MEMBER_MANAGE' | 'ORG_SETTINGS' | 'BILLING_MANAGE' | 'AUDIT_VIEW';

const ALL_PERMISSIONS: OrgPermission[] = [
  'MAILBOX_CONNECT', 'MAILBOX_READ', 'MAILBOX_SEND', 'MAILBOX_FOLDERS',
  'AUTOMATION_VIEW', 'AUTOMATION_EDIT', 'AUTOMATION_ACTIVATE', 'AUTOMATION_TEST',
  'APPROVAL_VIEW', 'APPROVAL_DECIDE',
  'RESOURCE_VIEW', 'RESOURCE_EDIT', 'SECRET_MANAGE',
  'MARKETPLACE_PUBLISH', 'MARKETPLACE_INSTALL',
  'MEMBER_INVITE', 'MEMBER_MANAGE', 'ORG_SETTINGS', 'BILLING_MANAGE', 'AUDIT_VIEW',
];

/** Operator base shared by MEMBER (agent) and EDITOR: work granted inboxes + decide the approval queue. */
const OPERATOR_BASE: OrgPermission[] = [
  'MAILBOX_READ', 'MAILBOX_SEND', 'MAILBOX_FOLDERS',
  'APPROVAL_VIEW', 'APPROVAL_DECIDE',
];

/**
 * Role → permission bundles. Kept in sync with the backend {@code OrgRole.permissions()}. Shape =
 * two entry roles + a builder ladder: OWNER = all; ADMIN = all except BILLING_MANAGE; EDITOR = operator
 * base + view/build/test/install (no activate); MEMBER (agent) = operator base only (inbox + approvals,
 * no automation/resource surface); VIEWER = read-only observer. MEMBER and VIEWER are parallel, not nested.
 */
export const ROLE_PERMISSIONS: Record<OrgRole, OrgPermission[]> = {
  OWNER: ALL_PERMISSIONS,
  ADMIN: ALL_PERMISSIONS.filter(p => p !== 'BILLING_MANAGE'),
  EDITOR: [
    ...OPERATOR_BASE,
    'AUTOMATION_VIEW', 'AUTOMATION_EDIT', 'AUTOMATION_TEST',
    'RESOURCE_VIEW', 'RESOURCE_EDIT', 'MARKETPLACE_INSTALL', 'AUDIT_VIEW',
  ],
  MEMBER: OPERATOR_BASE,
  VIEWER: [
    'MAILBOX_READ',
    'AUTOMATION_VIEW', 'RESOURCE_VIEW',
    'APPROVAL_VIEW', 'AUDIT_VIEW',
  ],
};

/** Summary of an organization the user belongs to (for the switcher). */
export interface Organization {
  id: string;
  name: string;
  slug: string | null;
  personal: boolean;
  myRole: OrgRole;
  memberCount: number;
  planName: string | null;
}

/** A member of an organization. */
export interface OrgMember {
  userId: string;
  email: string;
  fullName: string;
  role: OrgRole;
  status: MembershipStatus;
}

/** Full organization view with member roster. */
export interface OrganizationDetail {
  id: string;
  name: string;
  slug: string | null;
  personal: boolean;
  myRole: OrgRole;
  planName: string | null;
  members: OrgMember[];
}

export interface CreateOrganizationRequest {
  name: string;
}

export interface InviteMemberRequest {
  email: string;
  role: OrgRole;
}

/** A pending invitation for the current user to join an organization (awaiting accept/decline). */
export interface Invitation {
  organizationId: string;
  organizationName: string;
  role: OrgRole;
  invitedByName: string | null;
}

/** A member's read/send access to one organization mailbox (#4). One entry per org mailbox. */
export interface MailboxGrant {
  mailboxId: string;
  email: string;
  canRead: boolean;
  canSend: boolean;
}
