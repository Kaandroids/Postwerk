import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { I18nService } from '../../../../core/services/i18n.service';
import { UserService } from '../../../../core/services/user.service';
import { OrganizationService } from '../../../../core/services/organization.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { PageContentComponent } from '../page-content/page-content.component';
import { humanizeError } from '../../../../shared/utils/error.util';
import {
  MailboxGrant,
  OrgMember,
  OrgRole,
  OrganizationDetail,
} from '../../../../models/organization.model';

/**
 * Organization / team management page (#4 Phase D): roster, invitations, role changes, member
 * removal, leaving the org, and the per-member mailbox-grant editor. Management actions are gated
 * to Owner/Admin (the {@code canManage} computed); everyone else sees a read-only roster.
 */
@Component({
  selector: 'app-org-members',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent, ErrorBannerComponent, PageContentComponent],
  templateUrl: './org-members.component.html',
  styleUrl: './org-members.component.scss',
})
export class OrgMembersComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected org = inject(OrganizationService);
  private userService = inject(UserService);
  private confirmDialog = inject(ConfirmDialogService);

  /** Roles assignable via the UI, high→low (ownership transfer is handled separately, not offered here). */
  readonly assignableRoles: OrgRole[] = ['ADMIN', 'EDITOR', 'MEMBER', 'VIEWER'];

  detail = signal<OrganizationDetail | null>(null);
  loading = signal(true);
  error = signal('');

  inviteEmail = signal('');
  inviteRole = signal<OrgRole>('MEMBER');
  inviting = signal(false);

  // Mailbox-grant drawer state.
  grantMember = signal<OrgMember | null>(null);
  grants = signal<MailboxGrant[]>([]);
  grantsLoading = signal(false);
  grantsSaving = signal(false);

  readonly myUserId = computed(() => this.userService.profile()?.id ?? null);
  readonly members = computed(() => this.detail()?.members ?? []);
  readonly isPersonal = computed(() => this.detail()?.personal ?? false);
  readonly canManage = computed(() => {
    const role = this.detail()?.myRole;
    return role === 'OWNER' || role === 'ADMIN';
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.org.current().subscribe({
      next: d => {
        this.detail.set(d);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(humanizeError(err, this.i18n.t('org_members_load_error')));
        this.loading.set(false);
      },
    });
  }

  isMe(member: OrgMember): boolean {
    return member.userId === this.myUserId();
  }

  /** Owners/Admins implicitly have full mailbox access, so grants only apply to Editor/Member/Viewer. */
  hasGrantEditor(member: OrgMember): boolean {
    return member.role !== 'OWNER' && member.role !== 'ADMIN';
  }

  // ── Invite ──────────────────────────────────────────────────────────

  invite(): void {
    const email = this.inviteEmail().trim();
    if (!email || this.inviting()) return;
    this.inviting.set(true);
    this.error.set('');
    this.org.invite({ email, role: this.inviteRole() }).subscribe({
      next: () => {
        this.inviting.set(false);
        this.inviteEmail.set('');
        this.inviteRole.set('MEMBER');
        this.load();
      },
      error: err => {
        this.inviting.set(false);
        this.error.set(humanizeError(err, this.i18n.t('org_members_invite_error')));
      },
    });
  }

  onInviteRole(event: Event): void {
    this.inviteRole.set((event.target as HTMLSelectElement).value as OrgRole);
  }

  // ── Role change / removal / leave ───────────────────────────────────

  changeRole(member: OrgMember, event: Event): void {
    const role = (event.target as HTMLSelectElement).value as OrgRole;
    if (role === member.role) return;
    this.error.set('');
    this.org.setRole(member.userId, role).subscribe({
      next: updated => {
        this.detail.update(d => d
          ? { ...d, members: d.members.map(m => m.userId === updated.userId ? updated : m) }
          : d);
      },
      error: err => {
        this.error.set(humanizeError(err, this.i18n.t('org_members_role_error')));
        this.load(); // resync the dropdown to the server truth
      },
    });
  }

  async removeMember(member: OrgMember): Promise<void> {
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('org_members_remove'),
      message: this.i18n.t('org_members_remove_confirm', { name: member.fullName || member.email }),
      confirmText: this.i18n.t('org_members_remove'),
      cancelText: this.i18n.t('confirm_cancel'),
    });
    if (!ok) return;
    this.error.set('');
    this.org.removeMember(member.userId).subscribe({
      next: () => this.load(),
      error: err => this.error.set(humanizeError(err, this.i18n.t('org_members_remove_error'))),
    });
  }

  async leave(): Promise<void> {
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('org_members_leave'),
      message: this.i18n.t('org_members_leave_confirm', { name: this.detail()?.name ?? '' }),
      confirmText: this.i18n.t('org_members_leave'),
      cancelText: this.i18n.t('confirm_cancel'),
    });
    if (!ok) return;
    this.error.set('');
    this.org.leave().subscribe({
      next: () => {
        // The active org is gone — jump back to the personal workspace (switchOrg reloads).
        const personal = this.org.orgs().find(o => o.personal);
        if (personal) this.org.switchOrg(personal.id);
        else window.location.reload();
      },
      error: err => this.error.set(humanizeError(err, this.i18n.t('org_members_leave_error'))),
    });
  }

  // ── Mailbox grants ──────────────────────────────────────────────────

  openGrants(member: OrgMember): void {
    this.grantMember.set(member);
    this.grants.set([]);
    this.grantsLoading.set(true);
    this.error.set('');
    this.org.getMailboxGrants(member.userId).subscribe({
      next: g => {
        this.grants.set(g);
        this.grantsLoading.set(false);
      },
      error: err => {
        this.grantsLoading.set(false);
        this.error.set(humanizeError(err, this.i18n.t('org_members_grants_error')));
        this.grantMember.set(null);
      },
    });
  }

  toggleGrant(mailboxId: string, dim: 'read' | 'send'): void {
    this.grants.update(list => list.map(g => {
      if (g.mailboxId !== mailboxId) return g;
      if (dim === 'read') {
        const canRead = !g.canRead;
        // Send implies read — clearing read also clears send.
        return { ...g, canRead, canSend: canRead ? g.canSend : false };
      }
      const canSend = !g.canSend;
      return { ...g, canSend, canRead: canSend ? true : g.canRead };
    }));
  }

  saveGrants(): void {
    const member = this.grantMember();
    if (!member || this.grantsSaving()) return;
    this.grantsSaving.set(true);
    this.error.set('');
    this.org.setMailboxGrants(member.userId, this.grants()).subscribe({
      next: () => {
        this.grantsSaving.set(false);
        this.closeGrants();
      },
      error: err => {
        this.grantsSaving.set(false);
        this.error.set(humanizeError(err, this.i18n.t('org_members_grants_save_error')));
      },
    });
  }

  closeGrants(): void {
    this.grantMember.set(null);
    this.grants.set([]);
  }

  roleLabel(role: OrgRole): string {
    return this.i18n.t('org_role_' + role.toLowerCase());
  }
}
