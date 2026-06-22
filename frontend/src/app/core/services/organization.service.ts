import { Injectable, computed, inject, signal } from '@angular/core';
import { tap } from 'rxjs/operators';
import { ApiService } from './api.service';
import {
  CreateOrganizationRequest,
  Invitation,
  InviteMemberRequest,
  MailboxGrant,
  OrgMember,
  OrgPermission,
  OrgRole,
  Organization,
  OrganizationDetail,
  ROLE_PERMISSIONS,
} from '../../models/organization.model';

/**
 * Holds the active organization (tenant) for the session and exposes org/membership management.
 *
 * <p>The active org id is persisted to localStorage and attached to every API request as the
 * {@code X-Org-Id} header by the auth interceptor. Switching org reloads the app so all scoped
 * data is re-fetched for the new tenant.</p>
 */
@Injectable({ providedIn: 'root' })
export class OrganizationService {
  private readonly api = inject(ApiService);
  private static readonly STORAGE_KEY = 'postwerk.activeOrgId';

  readonly orgs = signal<Organization[]>([]);
  readonly activeOrgId = signal<string | null>(this.readStored());
  readonly activeOrg = computed(
    () => this.orgs().find(o => o.id === this.activeOrgId()) ?? null,
  );

  /** Pending invitations awaiting the user's accept/decline (for the org switcher). */
  readonly invitations = signal<Invitation[]>([]);

  /** The caller's role in the active organization, or null when none is selected. */
  readonly activeRole = computed<OrgRole | null>(() => this.activeOrg()?.myRole ?? null);

  /**
   * Whether the caller's active-org role grants {@code permission}. Mirrors the backend
   * {@code OrgRole.permissions()} bundles so the UI can hide actions/nav the role cannot use.
   *
   * <p>When the role is not known yet (orgs still loading) this returns {@code true} (optimistic) to
   * avoid a flash of hidden nav on every page load — the backend re-checks every mutation, so this is
   * a UX gate, not a security boundary. Every logged-in user owns at least their personal workspace,
   * so the role is null only during the brief initial load.</p>
   */
  can(permission: OrgPermission): boolean {
    const role = this.activeRole();
    return role ? ROLE_PERMISSIONS[role].includes(permission) : true;
  }

  /** Loads the user's organizations and ensures a valid active org is selected. */
  loadOrgs(): void {
    this.api.get<Organization[]>('/organizations').subscribe({
      next: orgs => {
        this.orgs.set(orgs);
        const active = this.activeOrgId();
        if (!active || !orgs.some(o => o.id === active)) {
          const fallback = orgs.find(o => o.personal) ?? orgs[0] ?? null;
          this.setActive(fallback?.id ?? null);
        }
      },
      error: () => { /* keep previous selection */ },
    });
  }

  /** Switches the active organization and reloads so all scoped data re-fetches. */
  switchOrg(id: string): void {
    if (id === this.activeOrgId() || !this.orgs().some(o => o.id === id)) return;
    this.setActive(id);
    window.location.reload();
  }

  create(request: CreateOrganizationRequest) {
    // Append to the local signal so the new org shows in the switcher immediately
    // (without this, switchOrg below would no-op because the org isn't in the list yet).
    return this.api.post<Organization>('/organizations', request).pipe(
      tap(created => this.orgs.update(list => [...list, created])),
    );
  }

  current() {
    return this.api.get<OrganizationDetail>('/organizations/current');
  }

  invite(request: InviteMemberRequest) {
    return this.api.post<OrgMember>('/organizations/members', request);
  }

  /** Loads the user's pending invitations into the {@link invitations} signal. */
  loadInvitations(): void {
    this.api.get<Invitation[]>('/organizations/invitations').subscribe({
      next: invs => this.invitations.set(invs),
      error: () => { /* leave previous list — non-critical */ },
    });
  }

  /** Accepts an invitation: the membership becomes ACTIVE and the org is returned for the switcher. */
  acceptInvitation(organizationId: string) {
    return this.api.post<Organization>(`/organizations/invitations/${organizationId}/accept`, {}).pipe(
      tap(joined => {
        this.invitations.update(list => list.filter(i => i.organizationId !== organizationId));
        this.orgs.update(list => list.some(o => o.id === joined.id) ? list : [...list, joined]);
      }),
    );
  }

  /** Declines an invitation: the INVITED membership is removed. */
  declineInvitation(organizationId: string) {
    return this.api.post<void>(`/organizations/invitations/${organizationId}/decline`, {}).pipe(
      tap(() => this.invitations.update(list => list.filter(i => i.organizationId !== organizationId))),
    );
  }

  setRole(userId: string, role: OrgRole) {
    return this.api.put<OrgMember>(`/organizations/members/${userId}`, { role });
  }

  removeMember(userId: string) {
    return this.api.delete<void>(`/organizations/members/${userId}`);
  }

  leave() {
    return this.api.post<void>('/organizations/leave', {});
  }

  /** Lists a member's per-mailbox grants — one entry per org mailbox with current read/send flags. */
  getMailboxGrants(userId: string) {
    return this.api.get<MailboxGrant[]>(`/organizations/members/${userId}/mailbox-grants`);
  }

  /** Replaces a member's per-mailbox grants (the backend persists only entries that grant something). */
  setMailboxGrants(userId: string, grants: MailboxGrant[]) {
    return this.api.put<void>(`/organizations/members/${userId}/mailbox-grants`, grants);
  }

  private setActive(id: string | null): void {
    this.activeOrgId.set(id);
    try {
      if (id) localStorage.setItem(OrganizationService.STORAGE_KEY, id);
      else localStorage.removeItem(OrganizationService.STORAGE_KEY);
    } catch { /* storage unavailable — keep in-memory only */ }
  }

  private readStored(): string | null {
    try {
      return localStorage.getItem(OrganizationService.STORAGE_KEY);
    } catch {
      return null;
    }
  }
}
