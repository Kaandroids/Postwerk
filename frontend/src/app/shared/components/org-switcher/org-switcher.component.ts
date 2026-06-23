import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { OrganizationService } from '../../../core/services/organization.service';
import { I18nService } from '../../../core/services/i18n.service';
import { IconComponent } from '../icon/icon.component';
import { humanizeError } from '../../utils/error.util';

/** Dropdown selector for switching the active organization (tenant) and creating new ones. */
@Component({
  selector: 'app-org-switcher',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, FormsModule],
  template: `
    <button class="org-switcher" type="button" [attr.aria-expanded]="open()" aria-label="Switch organization" (click)="toggle($event)" data-testid="org-switcher">
      <div class="org-current">
        <span class="org-typelabel"><app-icon name="building" /><span>{{ i18n.t('switcher_org') }}</span></span>
        @if (org.activeOrg(); as active) {
          <span class="org-mono">{{ monogram(active.name) }}</span>
          <div class="org-label">
            <span class="org-name">{{ active.name }}</span>
            <span class="org-role">{{ i18n.t('org_role_' + active.myRole.toLowerCase()) }}</span>
          </div>
          <app-icon [name]="open() ? 'arrowUp' : 'arrowDown'" />
        } @else {
          <span class="org-name org-empty">{{ i18n.t('org_none') }}</span>
          <app-icon name="arrowDown" />
        }
      </div>

      @if (org.invitations().length > 0) {
        <span class="org-invite-badge" data-testid="org-invite-badge">{{ org.invitations().length }}</span>
      }

      @if (open()) {
        <div class="org-dropdown" (click)="$event.stopPropagation()">
          @if (org.invitations().length > 0) {
            <div class="org-invites">
              <div class="org-invites-head">{{ i18n.t('org_invitations') }}</div>
              @for (inv of org.invitations(); track inv.organizationId) {
                <div class="org-invite" data-testid="org-invitation">
                  <span class="org-opt-mono">{{ monogram(inv.organizationName) }}</span>
                  <div class="org-invite-info">
                    <span class="org-invite-name">{{ inv.organizationName }}</span>
                    <span class="org-invite-sub">
                      {{ i18n.t('org_role_' + inv.role.toLowerCase()) }}@if (inv.invitedByName) { · {{ i18n.t('org_invited_by').replace('%name%', inv.invitedByName) }}}
                    </span>
                  </div>
                  <div class="org-invite-actions">
                    <button class="org-invite-accept" type="button" [disabled]="busyInvite() === inv.organizationId"
                            (click)="accept(inv.organizationId)" data-testid="org-invitation-accept" [title]="i18n.t('org_invite_accept')">
                      <app-icon name="check" />
                    </button>
                    <button class="org-invite-decline" type="button" [disabled]="busyInvite() === inv.organizationId"
                            (click)="decline(inv.organizationId)" data-testid="org-invitation-decline" [title]="i18n.t('org_invite_decline')">
                      <app-icon name="close" />
                    </button>
                  </div>
                </div>
              }
            </div>
          }

          @for (o of org.orgs(); track o.id) {
            <div class="org-option" [attr.data-active]="o.id === org.activeOrgId() ? '1' : '0'" (click)="select(o.id)" data-testid="org-option">
              <span class="org-opt-mono">{{ monogram(o.name) }}</span>
              <div class="org-option-info">
                <span class="org-option-name">{{ o.name }}</span>
                <span class="org-option-sub">{{ o.personal ? i18n.t('org_personal') : i18n.t('org_members_n').replace('%n%', o.memberCount + '') }}</span>
              </div>
              @if (o.id === org.activeOrgId()) {
                <app-icon name="check" />
              }
            </div>
          }

          @if (creating()) {
            <form class="org-create" (submit)="submitCreate($event)">
              <input
                class="org-create-input"
                [(ngModel)]="newName"
                name="newOrgName"
                [placeholder]="i18n.t('org_create_placeholder')"
                [disabled]="saving()"
                autocomplete="off"
                data-testid="org-create-input"
                (click)="$event.stopPropagation()"
              />
              <button class="org-create-go" type="submit" [disabled]="saving() || !newName.trim()" data-testid="org-create-submit">
                <app-icon name="check" />
              </button>
            </form>
            @if (createError()) {
              <div class="org-create-err" data-testid="org-create-error">{{ createError() }}</div>
            }
          } @else if (atFreeOrgLimit()) {
            <div class="org-limit-hint" data-testid="org-create-limit">
              <app-icon name="info" />
              <span>{{ i18n.t('org_create_limit') }}</span>
            </div>
          } @else {
            <button class="org-add" type="button" (click)="startCreate($event)" data-testid="org-create-open">
              <app-icon name="plus" />
              <span>{{ i18n.t('org_create') }}</span>
            </button>
          }
        </div>
      }
    </button>
  `,
  styles: `
    :host { position: relative; display: inline-block; }
    .org-switcher { appearance: none; border: 0; background: transparent; font-family: inherit; color: var(--fg); cursor: pointer; position: relative; text-align: left; }

    .org-current {
      display: flex; align-items: center; gap: 9px; height: 36px; padding: 0 10px;
      border: 0.5px solid var(--border); border-radius: 8px; background: var(--bg-2); min-width: 170px;
      transition: background 0.12s, border-color 0.12s;
      &:hover { background: var(--bg); border-color: var(--border-strong); }
    }

    .org-mono, .org-opt-mono {
      display: inline-flex; align-items: center; justify-content: center;
      width: 22px; height: 22px; border-radius: 6px; flex-shrink: 0;
      font-size: 11px; font-weight: 700; text-transform: uppercase;
      color: var(--accent);
      background: color-mix(in srgb, var(--accent) 16%, var(--bg-2));
    }

    .org-label { display: flex; flex-direction: column; min-width: 0; flex: 1; line-height: 1.2; }
    .org-name { font-size: 12.5px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 150px; }
    .org-role { font-size: 9.5px; font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase; color: var(--fg-subtle); }
    .org-empty { color: var(--fg-muted); }

    /* Compact mobile pill: replace the monogram + org name with an icon + "Org"
       type label (the active org is shown in the dropdown / elsewhere). */
    .org-typelabel { display: none; align-items: center; gap: 9px; font-size: 12.5px; font-weight: 600; color: var(--fg); }
    @media (max-width: 639.98px) {
      /* Narrow phones only (< sm): the full name/monogram of two pills won't
         fit, so collapse to an icon + short type label. From 640px up there's
         room for the normal switcher. */
      .org-current { min-width: 0; height: 34px; padding: 0 9px; gap: 8px; }
      .org-mono, .org-label, .org-empty { display: none; }
      .org-typelabel { display: inline-flex; }
      .org-typelabel app-icon {
        display: inline-flex; align-items: center; justify-content: center;
        width: 22px; height: 22px; border-radius: 6px; flex-shrink: 0;
        color: var(--accent);
        background: color-mix(in srgb, var(--accent) 16%, var(--bg-2));
      }
    }

    .org-dropdown {
      position: absolute; top: calc(100% + 6px); left: 0; min-width: 240px;
      background: var(--bg); border: 0.5px solid var(--border); border-radius: 10px;
      box-shadow: 0 8px 24px oklch(0 0 0 / 0.12); padding: 6px; z-index: 100;
    }

    .org-option {
      display: flex; align-items: center; gap: 10px; padding: 7px 9px; border-radius: 7px; cursor: pointer;
      transition: background 0.12s;
      &:hover { background: var(--bg-2); }
      &[data-active="1"] { color: var(--accent); }
    }
    .org-option-info { display: flex; flex-direction: column; min-width: 0; flex: 1; }
    .org-option-name { font-size: 13px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .org-option-sub { font-size: 11px; color: var(--fg-subtle); }

    .org-add {
      appearance: none; border: none; background: transparent; font-family: inherit; width: 100%; text-align: left;
      display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-top: 0.5px solid var(--border);
      margin-top: 4px; font-size: 13px; color: var(--fg-muted); cursor: pointer; border-radius: 7px;
      transition: background 0.12s, color 0.12s;
      &:hover { background: var(--bg-2); color: var(--fg); }
    }

    .org-create {
      display: flex; align-items: center; gap: 6px; padding: 6px; border-top: 0.5px solid var(--border); margin-top: 4px;
    }
    .org-create-input {
      flex: 1; min-width: 0; height: 30px; padding: 0 9px; font-size: 12.5px; font-family: inherit;
      border: 0.5px solid var(--border); border-radius: 7px; background: var(--bg-2); color: var(--fg);
      &:focus { outline: none; border-color: var(--accent); }
    }
    .org-create-go {
      display: inline-flex; align-items: center; justify-content: center; width: 30px; height: 30px; flex-shrink: 0;
      border: none; border-radius: 7px; background: var(--accent); color: #fff; cursor: pointer;
      &:disabled { opacity: 0.5; cursor: not-allowed; }
    }
    .org-create-err { padding: 4px 10px 2px; font-size: 11px; color: var(--danger); }

    .org-limit-hint {
      display: flex; align-items: flex-start; gap: 7px; padding: 8px 10px; border-top: 0.5px solid var(--border);
      margin-top: 4px; font-size: 11.5px; line-height: 1.35; color: var(--fg-subtle);
      app-icon { flex-shrink: 0; color: var(--fg-muted); }
    }

    /* Pending-invitation badge on the switcher button */
    .org-invite-badge {
      position: absolute; top: -5px; right: -5px; z-index: 1;
      min-width: 17px; height: 17px; padding: 0 4px; border-radius: 9px;
      display: inline-flex; align-items: center; justify-content: center;
      font-size: 10px; font-weight: 700; line-height: 1;
      color: #fff; background: var(--danger);
      box-shadow: 0 0 0 2px var(--bg-2);
    }

    /* Pending-invitation section in the dropdown */
    .org-invites { padding-bottom: 4px; margin-bottom: 4px; border-bottom: 0.5px solid var(--border); }
    .org-invites-head {
      font-size: 9.5px; font-weight: 700; letter-spacing: 0.05em; text-transform: uppercase;
      color: var(--fg-subtle); padding: 4px 9px 6px;
    }
    .org-invite { display: flex; align-items: center; gap: 10px; padding: 6px 9px; border-radius: 7px; }
    .org-invite-info { display: flex; flex-direction: column; min-width: 0; flex: 1; }
    .org-invite-name { font-size: 13px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .org-invite-sub { font-size: 11px; color: var(--fg-subtle); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .org-invite-actions { display: flex; align-items: center; gap: 5px; flex-shrink: 0; }
    .org-invite-accept, .org-invite-decline {
      display: inline-flex; align-items: center; justify-content: center; width: 26px; height: 26px;
      border: 0.5px solid var(--border); border-radius: 6px; background: var(--bg-2); cursor: pointer;
      transition: background 0.12s, color 0.12s, border-color 0.12s;
      &:disabled { opacity: 0.5; cursor: not-allowed; }
    }
    .org-invite-accept { color: var(--success); &:hover:not(:disabled) { background: color-mix(in srgb, var(--success) 16%, var(--bg-2)); border-color: var(--success); } }
    .org-invite-decline { color: var(--danger); &:hover:not(:disabled) { background: color-mix(in srgb, var(--danger) 16%, var(--bg-2)); border-color: var(--danger); } }
  `,
  host: {
    '(document:click)': 'closeDropdown()',
  },
})
export class OrgSwitcherComponent {
  protected org = inject(OrganizationService);
  protected i18n = inject(I18nService);

  open = signal(false);
  creating = signal(false);
  saving = signal(false);
  busyInvite = signal<string | null>(null);
  createError = signal('');
  newName = '';

  /**
   * Whether the account already owns the max free (Starter) organizations and cannot create another.
   * Mirrors the backend cap (2) — counts owned orgs on the free plan (personal workspace included);
   * paid orgs (a non-Starter plan) are excluded. The backend re-enforces this on create.
   */
  readonly atFreeOrgLimit = computed(() =>
    this.org.orgs().filter(o => o.myRole === 'OWNER' && (!o.planName || o.planName === 'STARTER')).length >= 2,
  );

  constructor() {
    this.org.loadOrgs();
    this.org.loadInvitations();
  }

  accept(orgId: string): void {
    if (this.busyInvite()) return;
    this.busyInvite.set(orgId);
    this.org.acceptInvitation(orgId).subscribe({
      next: joined => {
        this.busyInvite.set(null);
        this.open.set(false);
        this.org.switchOrg(joined.id); // jump into the org just joined (reloads)
      },
      error: () => this.busyInvite.set(null),
    });
  }

  decline(orgId: string): void {
    if (this.busyInvite()) return;
    this.busyInvite.set(orgId);
    this.org.declineInvitation(orgId).subscribe({
      next: () => this.busyInvite.set(null),
      error: () => this.busyInvite.set(null),
    });
  }

  monogram(name: string): string {
    const trimmed = (name || '').trim();
    return trimmed ? trimmed.charAt(0) : '?';
  }

  toggle(event: Event): void {
    event.stopPropagation();
    this.open.update(v => !v);
    if (!this.open()) this.creating.set(false);
  }

  select(id: string): void {
    this.open.set(false);
    this.org.switchOrg(id);
  }

  startCreate(event: Event): void {
    event.stopPropagation();
    this.createError.set('');
    this.creating.set(true);
  }

  submitCreate(event: Event): void {
    event.preventDefault();
    const name = this.newName.trim();
    if (!name || this.saving()) return;
    this.saving.set(true);
    this.createError.set('');
    this.org.create({ name }).subscribe({
      next: created => {
        this.saving.set(false);
        this.creating.set(false);
        this.newName = '';
        this.open.set(false);
        this.org.switchOrg(created.id);
      },
      error: err => {
        this.saving.set(false);
        this.createError.set(humanizeError(err, this.i18n.t('org_create_limit')));
      },
    });
  }

  closeDropdown(): void {
    this.open.set(false);
    this.creating.set(false);
  }
}
