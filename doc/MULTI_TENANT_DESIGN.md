# Multi-Tenant / Team Model — Design (Roadmap #4)

Status: **DESIGN APPROVED** (forks decided) — build not started.

## Decisions (confirmed)

| Fork | Decision |
|------|----------|
| Tenant boundary | **Organization** is the tenant. All resources move from `userId` ownership → `organizationId`. |
| Mailbox access | **Per-mailbox grants** (`MailboxGrant`): a member's read/send access is granted per inbox. Owner/Admin get implicit all-mailbox access. |
| Multi-org | **Yes** — a user may belong to many orgs; Slack-style switcher. Active org travels in `X-Org-Id` request header (JWT stays identity-only). |
| Plan / quota | **Per-organization.** `Plan` attaches to `Organization`; quotas counted per org. Seat-based billing deferred to #1. |

## Why a 2-level permission model
Org-role alone is insufficient for an email product: an intern may access `support@`/`sales@` but not `ceo@`.
- **Level 1 — Org role**: org-wide capabilities (member mgmt, automation editing, shared resources, billing).
- **Level 2 — Mailbox grant**: which specific inbox a member may **read** / **send-as**.

## Permission catalog (`Permission` enum)
Call sites use `require(Permission.X)` — never `requireRole(...)` — so custom roles can be added later without touching call sites.

| Domain | Permissions | Sensitive |
|---|---|---|
| Mailbox | `MAILBOX_CONNECT`, `MAILBOX_READ`*, `MAILBOX_SEND`*, `MAILBOX_FOLDERS` | SEND |
| Automation | `AUTOMATION_VIEW`, `AUTOMATION_EDIT`, `AUTOMATION_ACTIVATE`, `AUTOMATION_TEST` | ACTIVATE |
| Approvals (#3) | `APPROVAL_VIEW`, `APPROVAL_DECIDE`* | DECIDE |
| Shared resources | `RESOURCE_VIEW`, `RESOURCE_EDIT`, `SECRET_MANAGE` | SECRET |
| Marketplace | `MARKETPLACE_PUBLISH`, `MARKETPLACE_INSTALL` | |
| Org mgmt | `MEMBER_INVITE`, `MEMBER_MANAGE`, `ORG_SETTINGS`, `BILLING_MANAGE`, `AUDIT_VIEW` | |

`*` = mailbox-scoped (also requires the Level-2 grant). Sensitive perms tie into the #3 trust layer: who may flip a node to AUTO / approve a pending action is now a permission.

## Roles (`OrgRole` enum) → default permission bundles

| Role | Bundle |
|---|---|
| **OWNER** | All perms + billing + delete-org + transfer-ownership. 1 per org (transferable). Implicit all-mailbox. |
| **ADMIN** | All except billing/delete/transfer. Implicit all-mailbox. |
| **MEMBER** | AUTOMATION_VIEW/EDIT/TEST/ACTIVATE, RESOURCE_VIEW/EDIT, APPROVAL_VIEW/DECIDE, MARKETPLACE_INSTALL. MAILBOX_READ/SEND only on **granted** mailboxes. No member/secret/connect/billing. |
| **VIEWER** | AUTOMATION_VIEW, RESOURCE_VIEW, APPROVAL_VIEW, AUDIT_VIEW, MAILBOX_READ on granted mailboxes. Read-only. |

> Owner/Admin see all org mailboxes implicitly; Member/Viewer need explicit `MailboxGrant` rows → "intern can't see CEO inbox" falls out naturally.
> Activating a live (mail-sending) automation requires `AUTOMATION_ACTIVATE` **AND** `MAILBOX_SEND` on the target mailbox(es). This is the team version of the #3 safety story.

## Data model

New:
- `Organization(id, name, slug, planId, createdAt, deletedAt)`
- `Membership(id, organizationId, userId, role, status[INVITED|ACTIVE|SUSPENDED], invitedByUserId, createdAt)` — unique `(organizationId, userId)`
- `MailboxGrant(id, membershipId, mailboxId, canRead, canSend)` — unique `(membershipId, mailboxId)`
- Enums: `OrgRole`, `Permission`, `MembershipStatus`

Modified (add `organizationId`):
- `EmailAccount` (+ keep `connectedByUserId` for audit), `Automation`, `Category`, `Template`, `ParameterSet`, `WebhookEndpoint`, `MarketplaceListing`, `MarketplaceAcquisition`, `PendingAction`, `AiConversation`, `AiTokenUsage`.
- `Email`/`EmailAutomationTrace` scope through their account/automation's org (query via join; add `organization_id` only if needed for perf).
- `Plan` relationship moves: `User.plan` → `Organization.plan`.

## Request context & security
- JWT unchanged (identity: userId/email).
- New `OrgContextFilter` (after `JwtAuthenticationFilter`): reads `X-Org-Id`, loads the caller's `Membership` (Redis-cached), builds a request-scoped `OrgContext{ orgId, userId, role, permissions, mailboxGrants }`.
- New `AuthorizationService`: `require(Permission)`, `requireMailbox(mailboxId, READ|SEND)`. Throws 403 on failure.
- Repositories flip `findByIdAndUserId` → `findByIdAndOrganizationId`; services consult `OrgContext` instead of raw `userId`.

## Migration (personal-org backfill)
Every existing `User` gets an auto-created **personal Organization**; they become OWNER; all their `userId=X` resources are reassigned to that org; all their mailboxes are granted. Single-user accounts become 1-member orgs and notice nothing. `org.plan_id := user.plan_id` at migration time. No data loss, fully backward-compatible.

## Build sequence (each phase independently shippable, app never broken)

- **Phase A — Data model + backfill (no behavior change).** Org/Membership/MailboxGrant entities + enums; Flyway `V65` (tables), `V66` (nullable `organization_id` columns), `V67` (create personal orgs, backfill, then NOT NULL). App keeps using `userId`; `organization_id` populated but unused. Ships safely.
- **Phase B — Org context + org APIs + switcher.** `OrgContextFilter`, `AuthorizationService`, org endpoints (create/list-my-orgs/invite/accept/set-role/grants), frontend org switcher. Underneath still userId-scoped (every user has 1 org). Ships.
- **Phase C — Flip scoping to org + permission enforcement.** Repos/services switch to `organizationId`; `require(...)` checks at service layer; per-mailbox read/send + activate enforcement. Single-member orgs behave identically. Ships.
- **Phase D — Collaboration polish.** Invitations (email), member-management page, role editor, mailbox-grant UI, audit feed by actor.
- **Phase E (with #1) — Seat-based billing.**

## Naming note
Frontend `WorkspaceService` currently means "active **mailbox**". With Organization as the real tenant, rename that concept to "active mailbox" and reserve Organization/Workspace for the tenant + switcher.
