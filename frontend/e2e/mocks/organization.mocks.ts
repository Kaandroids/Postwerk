/** Mock data for the organization / team management surface (#4 Phase D). */

export const mockOrganizations = [
  { id: 'org-personal', name: 'Personal', slug: null, personal: true, myRole: 'OWNER', memberCount: 1, planName: 'PRO' },
  { id: 'org-1', name: 'Acme Inc', slug: 'acme', personal: false, myRole: 'OWNER', memberCount: 3, planName: 'PRO' },
];

export const mockOrgDetail = {
  id: 'org-1',
  name: 'Acme Inc',
  slug: 'acme',
  personal: false,
  myRole: 'OWNER',
  planName: 'PRO',
  members: [
    { userId: 'u1', email: 'owner@acme.com', fullName: 'Olivia Owner', role: 'OWNER', status: 'ACTIVE' },
    { userId: 'u2', email: 'mike@acme.com', fullName: 'Mike Member', role: 'MEMBER', status: 'ACTIVE' },
    { userId: 'u3', email: 'vera@acme.com', fullName: 'Vera Viewer', role: 'VIEWER', status: 'INVITED' },
  ],
};

/** A personal workspace — single member, no team-management affordances. */
export const mockPersonalOrgDetail = {
  id: 'org-personal',
  name: 'Personal',
  slug: null,
  personal: true,
  myRole: 'OWNER',
  planName: 'PRO',
  members: [
    { userId: 'u1', email: 'owner@acme.com', fullName: 'Olivia Owner', role: 'OWNER', status: 'ACTIVE' },
  ],
};

/** One row per org mailbox with the member's current read/send flags. */
export const mockMailboxGrants = [
  { mailboxId: 'mb-1', email: 'support@acme.com', canRead: true, canSend: false },
  { mailboxId: 'mb-2', email: 'sales@acme.com', canRead: false, canSend: false },
];

export const mockInvitedMember = {
  userId: 'u4', email: 'newbie@acme.com', fullName: '', role: 'MEMBER', status: 'INVITED',
};

export const mockMemberPromoted = {
  userId: 'u2', email: 'mike@acme.com', fullName: 'Mike Member', role: 'ADMIN', status: 'ACTIVE',
};

// ── Role-based access (new 5-role system) ─────────────────────────────────────

/**
 * A single-org list where the current user holds {@code role} — drives the sidebar/editor
 * permission gating in e2e (the active org's {@code myRole} feeds {@code OrganizationService.can()}).
 */
export const orgListAs = (role: string, planName: string | null = 'PRO') => [
  { id: 'org-1', name: 'Acme Inc', slug: 'acme', personal: false, myRole: role, memberCount: 3, planName },
];

/** Org detail mirroring {@link orgListAs} (for surfaces that read /organizations/current). */
export const orgDetailAs = (role: string) => ({
  id: 'org-1',
  name: 'Acme Inc',
  slug: 'acme',
  personal: false,
  myRole: role,
  planName: 'PRO',
  members: [
    { userId: 'u1', email: 'owner@acme.com', fullName: 'Olivia Owner', role: 'OWNER', status: 'ACTIVE' },
    { userId: 'u-self', email: 'me@acme.com', fullName: 'Current User', role, status: 'ACTIVE' },
  ],
});

/** Pending invitations for the current user (shown in the org switcher with a badge). */
export const mockInvitations = [
  { organizationId: 'org-invite-1', organizationName: 'Beta Team', role: 'EDITOR', invitedByName: 'Olivia Owner' },
  { organizationId: 'org-invite-2', organizationName: 'Gamma LLC', role: 'MEMBER', invitedByName: null },
];

/** The organization returned when an invitation is accepted (membership becomes ACTIVE). */
export const mockAcceptedOrg = {
  id: 'org-invite-1', name: 'Beta Team', slug: 'beta', personal: false, myRole: 'EDITOR', memberCount: 5, planName: 'PRO',
};

/** Two owned Starter orgs (personal included) → at the free-org creation cap. */
export const mockOrgsAtFreeLimit = [
  { id: 'org-personal', name: 'Personal', slug: null, personal: true, myRole: 'OWNER', memberCount: 1, planName: 'STARTER' },
  { id: 'org-1', name: 'Acme Inc', slug: 'acme', personal: false, myRole: 'OWNER', memberCount: 3, planName: 'STARTER' },
];

/** One owned Starter org → below the cap (the switcher offers the create button). */
export const mockOrgsBelowFreeLimit = [
  { id: 'org-personal', name: 'Personal', slug: null, personal: true, myRole: 'OWNER', memberCount: 1, planName: 'STARTER' },
];
