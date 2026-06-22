# Notification System — Design & Implementation Plan

> Status: **PLANNED** (design locked, not yet implemented — implementation starts once the
> in-flight parallel feature lands). Next migration: **V90**.
> Feature: a user-facing notification system — a persistent in-app inbox (bell + unread badge),
> optional email delivery, per-user preferences, and a `NOTIFY` automation node that lets a flow
> send a notification to a member of its organization.

## 0. Implementation status (live)

- ✅ **Slice 1 — Backend core (in-app)** — V90 migration (`notifications` + `notification_preferences`); enums `NotificationCategory`/`NotificationSeverity`/`NotificationType` (type carries category + default severity + title/body i18n keys); entities `Notification`/`NotificationPreference`; `NotificationRepository` (+ `markAllRead`/`deleteOlderThan`/`existsByUserIdAndDedupKey`) + `NotificationPreferenceRepository`; `NotificationService`(+impl) with per-recipient dedup + in-app pref gate (forced on for CRITICAL/ACTION_REQUIRED) + full preference matrix; `RecipientResolver` (owner + active OWNER/ADMIN via new `MembershipRepository.findActiveAdminUserIds`); DTOs; `NotificationController` (`GET /notifications`, `unread-count`, `PATCH {id}/read`, `read-all`, `GET/PUT preferences`); `ApprovalPendingEvent` + `NotificationEventListener` (`@Async @TransactionalEventListener(AFTER_COMMIT)`) wired from `PendingActionServiceImpl.park`. **Verified:** `test-compile` green; new `NotificationServiceImplTest` (7) + `RecipientResolverTest` (2) + updated `PendingActionServiceTest` (5) all pass. _Note: APPROVAL_PENDING deep-link uses `/dashboard/approvals` — verify the actual FE route in Slice 2._
- ✅ **Slice 2 — Frontend in-app** — `models/notification.model.ts`; `NotificationService` (signals `items`/`unreadCount`/`loading`; load/markRead/markAllRead/get+updatePreferences; optimistic updates) on `ApiService`; `NotificationCenterComponent` (topbar bell replacing the old placeholder + unread badge + dropdown inbox: severity icons, relative time, deep-link-on-click, mark-all-read, empty/loading states, 60s unread poll, click-outside/Esc close); generic `ToastService` + `ToastContainerComponent` (mounted in `dashboard-layout` next to confirm-dialog — kept off the initial bundle); notification preferences matrix (category × In-app/Email) added to the settings page (auto-saves per toggle, optimistic + error toast); all `notif_*` + `notif_type_*` i18n keys (DE+EN). **Verified:** `ng build` green (only pre-existing `email-detail` NG8107/8102 warnings + the pre-existing initial-bundle budget warning; toast moved off app-root to avoid growing it). _Follow-ups: real-time SSE push (currently poll-based); e2e mocks + specs._
- ✅ **Slice 3 — Remaining system events** — 3 domain events (`AutomationFailedEvent`, `AiUsageRecordedEvent`, `MailboxSyncErrorEvent`) published from `AutomationExecutorServiceImpl` (FAILED catch), `AiUsageService` (after each usage save, org-attributed only), and `EmailSyncService.recordSyncFailure` (only on healthy→failing transition). Three new `@Async @EventListener` handlers in `NotificationEventListener`: AUTOMATION_FAILED (owner+admins, hourly dedup), QUOTA via `QuotaService.getUsage` → QUOTA_WARNING(≥80%)/QUOTA_EXCEEDED(≥100%) (owner+admins, once per org per month per tier), MAILBOX_AUTH/CONN_ERROR (owner+admins, daily dedup). New `MembershipRepository.findActiveAdminUserIds`/`findActiveUserIdsByOrgAndRole`. **Verified:** full affected-test run green (executor 23 + notif 9 + pending 5 = 37); the quota seam uses a plain `@EventListener` (not AFTER_COMMIT) because `AiUsageService` is `@Async` with no surrounding tx. _Email-account deep link uses `/dashboard/email-accounts` — verify route in FE pass._
- ✅ **Slice 4 — `NOTIFY` automation node** — **Backend:** `NodeType.NOTIFY` (in `ACTION_TYPES`); `NotifyNodeProcessor` (recipientType USER/OWNER/ADMINS, runtime active-membership validation, variable-interpolated title/message, severity/category overrides, dry-run `SIMULATED` + mockable, injects `notify_<key>.sent`/`.recipientCount`, routes `success`/`fail`); `AutomationValidator` codes `NOTIFY_NO_RECIPIENT`/`NOTIFY_NO_MESSAGE` + dangling-token scan + `notify_<id>` namespace; `MembershipRepository.findActiveUserIdsByOrgAndRole`; AI prompt docs (`node-config-reference.txt` + `tools-reference.txt`: config schema, handles, injected vars, test-status row, validator codes, decision-table row). `NotifyNodeProcessorTest` 5/5. **Frontend** (mirrors VECTOR_SEARCH; palette `bell`/`#6d28d9` in actions group): `automation.model.ts` (`NotifyNodeConfig` + default + palette); node-config-panel NOTIFY section (recipientType select, org-member dropdown via `OrganizationService.current().members`, title/message via `VariableComboboxComponent`, severity/category/linkUrl) — config keys match backend exactly; canvas ports (input + `success`/`fail`); `automation-test-panel` `STATUS_BY_TYPE.NOTIFY=['SIMULATED','ERROR']` + `formatNodeDetails`; `testPanelNodes` assertable `sent`/`recipientCount`; `VariableGraphService` emits `notify_<id>.*`; `AutomationLintService` NOTIFY codes; `auto_node_notify*`/`auto_notify_*`/`auto_lint_notify_*` i18n (DE+EN). **Verified:** full backend suite **547 green**; `ng build` green (only pre-existing warnings). _e2e mocks/specs still a follow-up._
  - **Content = template OR manual (like SEND_EMAIL):** the NOTIFY content section now has a Vorlage/Manuell toggle (reuses the shared `getContentSource`/`setContentSource` + `templates()`). Backend `NotifyNodeProcessor` resolves content by `templateId` presence (template subject→title, body→`htmlToText`→message) else inline title/message; `NotifyNodeProcessorTest` 6/6. Validator + FE lint `NOTIFY_NO_MESSAGE` are template-aware (a selected template satisfies content). Default `contentSource` = MANUAL. AI doc notes the `templateId` alternative.
- ⬜ **Slice 5 — Email channel** — `NotificationEmailComposer` (recipient-language i18n templates), preference gating, throttle/digest for non-critical. **NOTE: the `EmailSender` abstraction now EXISTS** — built by the email-verification feature (`service/email/`: `EmailSender` + `SmtpEmailSender`/`LoggingEmailSender` via `EmailConfig`, `MailTemplateService` for `/email/*` templates, Mailpit dev / Resend prod via `app.mail.*`). Slice 5 = inject `EmailSender` into an `EmailChannel` + write the composer; do NOT build a second mailer.

## 1. Motivation

The product produces ~15–20 distinct events a user genuinely needs to know about, but today there
is **no way to surface them**: an automation fails silently in a trace, an AI budget hits its cap
mid-month, a mailbox's IMAP token expires and stops syncing, a supervised-mode action sits waiting
for human approval — and the user only finds out by manually opening a page. There is no inbox, no
badge, no email, no in-flow alerting.

We want one system that:
1. **Persists** notifications per user (offline-safe inbox with read/unread state).
2. **Delivers** them in-app (bell) now, and by **email** for critical/action-required events.
3. Is **user-configurable** (per category × per channel, from settings).
4. Exposes a **`NOTIFY` automation node** so users can route their own in-flow alerts to a
   colleague in the org ("if classified as URGENT complaint → notify the support lead").

## 2. What already exists (reuse, don't rebuild)

| Building block | Where | Reuse for |
|---|---|---|
| **SSE streaming** (virtual-thread `SseEmitter`, typed events, per-user concurrency cap) | `AiAssistantController.java:57`, `AiAssistantServiceImpl.chatStream:344`, FE `ai-chat.service.ts` | Slice-2/Phase-real-time live push to open clients |
| **Async off-request write** (`@Async`, bounded executor) | `AuditWriter.java`, `AsyncExecutionConfig.java` | Channel dispatch off the request/exec thread |
| **Audit log** (`AuditAction` 110 vals, `AuditService`, org-scoped, GDPR retention) | `model/AuditAction.java`, `service/AuditService.java` | **Sibling, not parent** — derive notifications from the *same* domain events, do NOT reuse the table |
| **Sync-status tracking** (`lastSyncStatus` OK/CONN_ERROR/AUTH_ERROR, `lastError`) | `EmailSyncService.java:100-129`, migration V75 | Mailbox notifications — fire on **transition** |
| **Quota usage** (`getUsage` costUsedCents/cap, `checkAiTokenQuota`) | `QuotaServiceImpl.java:79,155` | Quota 80%/100% notifications |
| **Pending actions** (`ApprovalStatus` PENDING/APPROVED/REJECTED/EXPIRED) | `model/PendingAction.java`, executor `:847`, V62/V63 | The highest-value `ACTION_REQUIRED` source |
| **Org membership** (`MembershipStatus`, `OrgRole` OWNER/ADMIN/MEMBER/VIEWER) | `model/Membership.java`, `OrganizationService.java` | `RecipientResolver` (owner + admins) + NOTIFY-node recipient picker |
| **Announcements** (admin → broadcast) | migration V79 | Fold in later as a `SYSTEM`-category source — do **not** duplicate |

### Gaps to fill
- ✅ **System/transactional email now EXISTS** — the email-verification feature added the `EmailSender` abstraction (`service/email/`, SMTP via Spring `JavaMailSender`, Mailpit dev / Resend prod, gated by `app.mail.enabled`). Slice 5 reuses it; it no longer needs to introduce one.
- ❌ **No generic toast/snackbar service** (only sticky `quota-banner`/`error-banner` + `confirm-dialog`). Slice 2 adds `ToastService`.
- ❌ **No web-push / service worker** (out of scope; future phase).

## 3. Locked design decisions

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | **`Notification` is its own entity, NOT `AuditLog`.** Both are derived from the same domain events. | Audit = append-only compliance log (admin); notification = user-facing UX with read state + per-recipient fan-out + deep links. Different lifecycle, different consumer. |
| D2 | **DB is the source of truth; SSE/poll are delivery optimizations.** Client polls on load (sees the bell filled); SSE only pushes live to *open* clients. | Correctness is independent of the real-time transport → works today under single-instance (`max-instances=1`), scales later via Redis pub/sub fan-out without changing the data model. No notification is ever lost, only (briefly) delayed until next poll/reconnect. |
| D3 | **Producers are decoupled via Spring `ApplicationEventPublisher` domain events;** `NotificationService` is an `@EventListener`. | DIP/SOLID — the executor/quota/sync services never depend on notification logic. Each new source = publish one event. Mirrors the existing audit decoupling. |
| D4 | **Store i18n key + JSON params, NOT rendered text** (`title_key`, `body_key`, `params`). | App is bilingual (DE+EN); a notification must render in the *recipient's current language*, possibly long after creation. (Audit can store flat text; it is admin-only.) |
| D5 | **Channels are a Strategy** (`NotificationChannel` interface): `InAppChannel`, `EmailChannel`, (later) `WebPushChannel`. **In-app always writes the row** (it is the inbox); per-channel preference gates email/push. | Open/Closed: add a channel without touching producers or core. In-app is the durable record; other channels are projections. |
| D6 | **Recipient resolution: source owner + active `OWNER`/`ADMIN` of the org (deduped).** Personal events (your quota/account) → you only. `NOTIFY` node → explicit config target. **One row per recipient (fan-out).** | User's chosen policy. Team visibility for shared-org failures without spamming `MEMBER`/`VIEWER`. Admin set per org is small → bounded write amplification. |
| D7 | **Dedup/throttle is mandatory.** Mailbox fires only on **state transition** (`OK→AUTH_ERROR`), never per 5-min poll (uses already-tracked `lastSyncStatus`). Automation failures dedup by `dedup_key=(userId,type,automationId)` + cooldown; optional hourly digest at scale. | Without this, a broken mailbox or a hot-looping automation buries the inbox. The state needed for transition-detection already exists. |
| D8 | **Preferences = normalized `(user_id, category)` row with per-channel booleans**, lazily defaulted. Defaults: in-app ON for all; email ON for `CRITICAL`/`ACTION_REQUIRED`, OFF for `INFO`/`SUCCESS`. | Simple matrix UI (rows=category, cols=channel). Relational, queryable, matches house style. |
| D9 | **In-app cannot be disabled for `CRITICAL`/`ACTION_REQUIRED`;** email is always opt-out-able. | Safety: a muted-everything user must still see "your mailbox disconnected" / "action needs approval" in the bell. |
| D10 | **`NOTIFY` is a new `NodeType`** — like `SEND_EMAIL` but emits an in-app/email notification to an org member. Recipient validated as an **active member at runtime**. Dry-run returns `SIMULATED` + is mockable. | Turns automations into an alerting/workflow tool. Reuses the variable system for `title`/`message` interpolation. Membership can change after build → validate at execution, fail gracefully. |
| D11 | **Email channel is abstracted behind `EmailSender`** (profile-based: dev Mailpit / prod Resend) and ships **last** (Slice 5). In-app goes end-to-end first (Slices 1–4). | The SMTP/Resend gap must not block the whole feature. Aligns with the planned GCP deployment's transactional-email work. |
| D12 | **Channel dispatch runs off the request/exec thread** (reuse `@Async` + bounded executor, the `AuditWriter` pattern). | A slow SMTP send must never stall an automation run or an HTTP request. |
| D13 | **`announcements` (V79) is folded in later as a `SYSTEM`-category source**, not re-modeled. | Admin broadcast already exists; it becomes one producer that fans out a `SYSTEM` notification, reusing this pipeline. |

### Caveats baked into the plan
- **Multi-instance:** the in-memory SSE registry only reaches clients connected to the *same* instance. Fine while `max-instances=1`; when scaling, fan out delivery via Redis pub/sub. Because of D2 nothing is lost — a client on another instance just sees it on next poll/reconnect.
- **NOTIFY prompt-injection surface:** `title`/`message` interpolate possibly attacker-controlled email content. Recipient is internal (lower risk), but the node must never trigger a *side effect* from that content, and the FE must render notification text as **plain text (escaped)**, never HTML.
- **Write amplification:** owner+admins fan-out × dedup is bounded; the `dedup_key` cooldown is the backstop against storms.
- **GDPR:** notifications carry user data → add a retention policy to `DataRetentionService` (e.g. hard-delete read notifications older than N days, unread older than M). Config under `app.gdpr.*`.
- **Email deliverability:** no real sender until Slice 5; keep `EmailSender` behind a profile so dev uses Mailpit and prod plugs Resend keys later.

## 4. Data model (migration V90)

```sql
-- V90__notifications.sql
CREATE TABLE notifications (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL,                 -- recipient
    organization_id  UUID,                          -- context org (nullable for personal events)
    category         VARCHAR(32)  NOT NULL,         -- AUTOMATION|APPROVAL|QUOTA|MAILBOX|TEAM|MARKETPLACE|SYSTEM
    type             VARCHAR(64)  NOT NULL,         -- AUTOMATION_FAILED, APPROVAL_PENDING, QUOTA_80, MAILBOX_AUTH_ERROR, ...
    severity         VARCHAR(16)  NOT NULL,         -- INFO|SUCCESS|WARNING|CRITICAL|ACTION_REQUIRED
    title_key        VARCHAR(120) NOT NULL,         -- i18n key (rendered in recipient's language)
    body_key         VARCHAR(120),                  -- i18n key (nullable)
    params           JSONB NOT NULL DEFAULT '{}',   -- interpolation params, e.g. {"automationName":"Invoices","count":"3"}
    link_url         VARCHAR(512),                  -- deep link, e.g. /dashboard/automations/<id>
    payload          JSONB NOT NULL DEFAULT '{}',   -- entity refs for the client, e.g. {"automationId":"...","traceId":"..."}
    dedup_key        VARCHAR(200),                  -- (userId:type:entityId) for dedup/cooldown
    read_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ                    -- optional auto-expiry
);
CREATE INDEX idx_notif_user_created   ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notif_user_unread    ON notifications(user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX idx_notif_dedup          ON notifications(dedup_key, created_at DESC) WHERE dedup_key IS NOT NULL;

CREATE TABLE notification_preferences (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    category        VARCHAR(32) NOT NULL,           -- one row per (user, category)
    in_app_enabled  BOOLEAN NOT NULL DEFAULT true,
    email_enabled   BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (user_id, category)
);
```

Notes:
- **No row needed for a default** — absence of a `notification_preferences` row means "use the
  category default" (D8). Rows are created lazily on first `PUT /preferences`.
- `NOTIFY` node needs **no migration** — it is a node `config` JSON in the existing `automation_nodes` table; only the `NodeType` enum + executor are new.

### Enums (backend `model/enums/`)
- `NotificationCategory`: `AUTOMATION, APPROVAL, QUOTA, MAILBOX, TEAM, MARKETPLACE, SYSTEM`
- `NotificationSeverity`: `INFO, SUCCESS, WARNING, CRITICAL, ACTION_REQUIRED`
- `NotificationType`: the concrete event keys (see §6 catalog). `type` drives `title_key`/`body_key`/`category`/default-severity via a small lookup so producers stay terse.

## 5. Architecture

```
[1] Event layer (producers — already exist)
    AutomationExecutorServiceImpl · QuotaServiceImpl · EmailSyncService · PendingAction lifecycle · NotifyNodeExecutor (new)
        │  applicationEventPublisher.publishEvent(new AutomationFailedEvent(orgId, ownerId, automationId, ...))
        ▼
[2] Notification core (NEW)
    NotificationService  @EventListener(*Event)
      ├─ RecipientResolver        → Set<userId>  (owner + active OWNER/ADMIN | personal | explicit)
      ├─ dedup/throttle           → dedup_key + cooldown; mailbox = transition-only
      ├─ persist Notification     → one row per recipient (DB = source of truth, D2)
      └─ dispatch to channels (@Async, D12)
        ▼
[3] Delivery channels (Strategy — NotificationChannel)
    ├─ InAppChannel   → already persisted; nudges SSE if recipient has an open stream      (Slice 1/2)
    ├─ Realtime (SSE) → GET /notifications/stream, reuses AiAssistant SSE pattern           (Slice 2)
    ├─ ToastService   → FE-only, ephemeral session feedback (separate from the inbox)        (Slice 2)
    ├─ EmailChannel   → EmailSender (Mailpit dev / Resend prod), gated by prefs + severity   (Slice 5)
    └─ WebPushChannel → service worker + Web Push                                             (future)
```

### REST API (`/api/v1/notifications`)
| Method | Path | Purpose |
|---|---|---|
| `GET` | `/notifications?unread={bool}&page=&size=` | Paginated list (org-agnostic personal inbox; org badge per item) |
| `GET` | `/notifications/unread-count` | Badge count |
| `PATCH` | `/notifications/{id}/read` | Mark one read |
| `POST` | `/notifications/read-all` | Mark all read |
| `GET` | `/notifications/preferences` | Matrix (category × channel), defaults filled |
| `PUT` | `/notifications/preferences` | Upsert preference rows |
| `GET` | `/notifications/stream` | **(Slice 2)** SSE live push |

## 6. Event catalog → category / severity

| type | category | severity | Producer (file:line) | Recipients | Dedup |
|---|---|---|---|---|---|
| `APPROVAL_PENDING` | APPROVAL | ACTION_REQUIRED | `AutomationExecutorServiceImpl:847` (park) | owner + admins | per pendingActionId |
| `APPROVAL_DECIDED` | APPROVAL | INFO | PendingAction APPROVED/REJECTED/EXPIRED | the approver/owner | — |
| `AUTOMATION_FAILED` | AUTOMATION | WARNING | `AutomationExecutorServiceImpl:381` (trace FAILED) | owner + admins | (user,type,automationId)+cooldown |
| `QUOTA_80` | QUOTA | WARNING | `QuotaServiceImpl.getUsage:155` (cross 80%) | org owner + admins | once per billing period |
| `QUOTA_EXCEEDED` | QUOTA | CRITICAL | `QuotaServiceImpl.checkAiTokenQuota:79` | org owner + admins | once per billing period |
| `MAILBOX_AUTH_ERROR` | MAILBOX | CRITICAL | `EmailSyncService:117` (`OK→AUTH_ERROR`) | account owner + admins | transition-only |
| `MAILBOX_CONN_ERROR` | MAILBOX | WARNING | `EmailSyncService:117` (`OK→CONN_ERROR`) | account owner + admins | transition-only |
| `TEAM_INVITED` | TEAM | ACTION_REQUIRED | `OrganizationService` invite | invited user | per membership |
| `TEAM_ROLE_CHANGED` | TEAM | INFO | role grant/change | the member | — |
| `TEAM_SUSPENDED` | TEAM | WARNING | membership SUSPENDED / org suspend | the member | — |
| `MARKETPLACE_INSTALLED` | MARKETPLACE | INFO | acquisition ACTIVE | listing author | — |
| `MARKETPLACE_REVIEWED` | MARKETPLACE | INFO | review submitted | listing author | — |
| `NOTIFY_NODE` | (node's chosen category, default `SYSTEM`) | node config | `NotifyNodeExecutor` (new) | explicit org member/role | per node-run |
| `ANNOUNCEMENT` | SYSTEM | INFO | announcements (V79), folded later | broadcast | — |

> v1 priority set (user-chosen): **APPROVAL · AUTOMATION_FAILED · QUOTA · MAILBOX** + the **NOTIFY node** + **preferences**. The rest land incrementally on the same pipeline.

## 7. `NOTIFY` automation node (Slice 4)

A side-effect node, sibling to `SEND_EMAIL`, that emits a notification to a member of the
automation's own organization.

```
NodeType: NOTIFY            executor: NotifyNodeExecutor
config: {
  recipientType: "USER" | "ROLE" | "OWNER",   // who inside the org
  recipientUserId?: "<uuid>",                  // when USER
  recipientRole?:   "OWNER" | "ADMIN",         // when ROLE → fan-out to that role
  title:    "Neue Bestellung: {{trigger.from}}",   // variable interpolation (existing system)
  message:  "{{extraction_1.summary}}",
  severity: "INFO" | "WARNING" | "CRITICAL",   // default INFO
  category: "SYSTEM",                          // default SYSTEM
  channels: ["IN_APP","EMAIL"],                // or omit → respect recipient prefs
  linkUrl?: "/dashboard/..."
}
```
- **Runtime safety:** resolve recipient and assert they are an **active member** of the
  automation's org; if not → node `failure` handle + trace error (no throw).
- **Ports:** input + `success`/`failure` handles (action-node shape).
- **Injected vars (downstream):** `notify_<nodeId>.sent` (bool), `notify_<nodeId>.recipientCount`.
- **Dry-run / test:** does NOT send; returns `resultDetail` `{recipient, renderedTitle, renderedMessage, mocked?}` with status `SIMULATED`; mockable per the test/sim mock-engine design.
- **Validator codes:** `NOTIFY_NO_RECIPIENT` (err), `NOTIFY_NO_MESSAGE` (err) — add to BE `AutomationValidator` **and** FE `AutomationLintService`.

## 8. Frontend

- `models/notification.model.ts` — `Notification`, `NotificationPreference`, enums, `NotifyNodeConfig` (with `[key:string]: unknown` index sig).
- `core/services/notification.service.ts` — signals `items`, `unreadCount`, `loading`; `load()`, `markRead()`, `markAllRead()`, `prefs`; SSE consumer (Slice 2) reusing the `ai-chat.service.ts` fetch-stream pattern. Renders `title_key`/`body_key` via `I18nService.t(key, params)`.
- `core/services/toast.service.ts` — **the missing generic toast**: queue, auto-dismiss, stacking, severity color; `+` a `<app-toast-container>` mounted once at app root.
- `features/notifications/` — `notification-center` component: topbar **bell** (next to the AI-limiter widget) + unread badge + dropdown list (grouped by day, org badge, deep-link on click, "mark all read"). Empty-state per house pattern.
- **Settings → preferences matrix:** rows = category, cols = In-app / E-Mail toggles; in-app toggle disabled (forced-on) for `CRITICAL`/`ACTION_REQUIRED` rows (D9).
- **NOTIFY node wiring** (per the project's Automation-Node checklist): `NODE_PALETTE` (icon `bell`, color) + `NODE_DEFAULT_CONFIG` + `NotifyNodeConfig` → node-config-panel section (recipient picker fed by org-member list + `VariableComboboxComponent` for `title`/`message` + severity/channel controls) → `STATUS_BY_TYPE` (`['SIMULATED','ERROR']`) + `formatNodeDetails` (recipient/title/message) + `testPanelNodes` (assertable `sent`, `recipientCount`).
- **i18n DE+EN:** `notif_*` (center, settings, each `type`'s title/body), `auto_node_notify*` (palette/config), validator codes `auto_lint_notify_no_recipient`/`_no_message`. i18n placeholder format = `%key%`.

## 9. AI / wizard authorability (Slice 4)

- `tools-reference.txt` + `node-config-reference.txt` (shared assistant+wizard): document `NOTIFY`
  — config schema, `success`/`failure` handles, injected `notify_<id>.*` vars, test-status row,
  validator codes. The validator→AI self-correction loop covers it automatically once
  `AutomationValidator` has the `NOTIFY` cases.

## 10. Build order (vertical slices — each ends shippable)

1. **BE core (in-app):** V90 → entities + enums → `NotificationService`(if+impl) + `RecipientResolver` → repository → controller (list / unread-count / read / read-all / prefs) → wire **APPROVAL_PENDING** via `ApplicationEventPublisher` as the proof event. `mvnw compile` + unit tests (resolver fan-out, dedup, prefs defaults).
2. **FE in-app:** model → `NotificationService` → notification-center (bell+dropdown) → `ToastService` + container → settings preferences matrix → i18n. `ng build`.
3. **Remaining system events:** AUTOMATION_FAILED, QUOTA_80/EXCEEDED, MAILBOX_AUTH/CONN (transition) → publish events + dedup/throttle. Tests per producer.
4. **NOTIFY node:** §7 + §8 node wiring + §9 docs. Validator + dry-run + mock + e2e.
5. **Email channel:** `EmailSender` (Mailpit dev / Resend prod) + `NotificationEmailComposer` (recipient-language templates) + preference/severity gating + non-critical throttle/digest. Add notification retention to `DataRetentionService`.

## 11. Open questions (defer until implementation)
- Email **digest cadence** for non-critical (immediate vs hourly roll-up) — start immediate-for-critical, digest-for-rest.
- Should `TEAM_INVITED` also email even before the invited user has an account? (depends on invite flow — likely yes, via Slice 5.)
- Notification **retention windows** (read vs unread) — pick concrete N/M when wiring `DataRetentionService`.
