# Product & Architecture Roadmap

Gaps to close on the way from "strong core engine" to "a business will trust it and pay for it."
Tackled one at a time. **Agreed sequence: start with #3 (trust layer); do #1 (payment) and #2
(OAuth email) LAST.**

## Sequence
1. **#3 — AI trust / human-in-the-loop layer** ← _current focus_
2. #4 — Team / multi-tenant (org, roles, shared automations, shared inbox)
3. #5 — Production observability & activity (per-automation run history, AI reasoning, debugging)
4. #6 — Architecture scale: event-driven sync (IMAP IDLE/push), message queue, idempotency
5. #7 — AI cost control at scale (batching, embedding cache, cheap-path heuristics)
6. #8 — Object storage for emails/attachments (S3) + email-table partitioning/archival
7. #9 — Native connectors (Slack/CRM/calendar/ticketing) on top of webhooks/integrations
8. #10 — Deliverability (SPF/DKIM guidance, bounce handling, send logs) + value analytics/ROI
9. #11 — Infra hardening: TLS, monitoring/metrics/tracing, backups, KMS/key rotation
10. **#2 — OAuth email connect (Gmail/Outlook one-click)** ← near the end (per decision)
11. **#1 — Payment / billing (Stripe/Paddle) + entitlement enforcement** ← last (per decision)

## Customer / usage gaps
- **#1 No payment** — plans exist (STARTER/PRO/€5 cap) but no Stripe/Paddle; not monetizable yet.
- **#2 IMAP/SMTP credentials only** — no OAuth; manual host/port/app-password is the biggest
  adoption + trust barrier for non-technical users.
- **#3 No human-in-the-loop / feedback for AI auto-actions** — AI auto-replies/forwards/moves real
  customer email; a misclassification → wrong irreversible action. No approval queue, no
  confidence-gated execution, no correction→learning loop, no per-action undo. (Detail below.)
- **#4 Single-user only** — no org/team/roles/shared automations/shared inbox.
- **#5 Weak production observability (customer side)** — no clear "what did my automation do / why"
  run history with the AI's reasoning.
- **#10 No value analytics** — no ROI surface ("X hours saved, Y emails auto-handled").
- **#9 No native connectors** — only raw webhooks/callable integrations.
- **#10 Deliverability** — sending via user SMTP with no SPF/DKIM guidance, bounce handling, or send logs.
- AI cost transparency for the customer; mobile/push notifications.

## Architecture gaps
- **#6 Email sync = polling** (not IMAP IDLE/push) — won't scale to thousands of accounts.
- **#7 Per-email Gemini calls** (classify+extract) — cost explodes with volume; needs batching,
  embedding cache, cheap-path heuristics.
- **#6 No message broker** — scheduled-poll + async, no Kafka/RabbitMQ/SQS → weak retry/DLQ/ordering/
  exactly-once; idempotency for email actions is undefined (double-send risk on retry).
- **#8 Emails + attachments in Postgres** (TEXT/BYTEA) — won't scale; move attachments to S3,
  partition/archive the emails table.
- **#11 Single Postgres, single encryption key** — no read replica/partitioning; no KMS/key rotation.
- **#11 No observability** — no metrics/tracing/structured logs (can't debug "why wasn't this email
  processed").
- **#4 Multi-tenant isolation** — queries are user/account-scoped but there is no org layer; retrofitting
  true tenancy later is harder — design for it now.
- **#11 TLS / monitoring / backups** — deployment-layer, deferred.

## What is already strong (keep)
Node-based flow editor + AI assistant, marketplace, callable integrations, cost-based AI quota,
GDPR/data-retention, validator/linter, audit log, shared AI tool/prompt source. The core is mature;
the gaps are the expected "MVP → commercial/scaled product" work.

---

## #3 — AI trust / human-in-the-loop layer (current focus)

**Problem.** When an automation is `ACTIVE`, real incoming email flows through and actions
(EMAIL_ACTION REPLY/FORWARD/MOVE, SEND_EMAIL, LABEL) **fire automatically**, driven by probabilistic
AI nodes (CATEGORIZE/EXTRACT). A misclassification → a wrong, often **irreversible** action on a real
customer (auto-reply with wrong info, forward confidential mail, move an important mail to trash).
Today the only states are `TESTING` (everything SIMULATED + user grades it) and `ACTIVE` (everything
auto). There is nothing in between, and the grades captured in TESTING are not used to improve the AI.

**Build on what exists:** `AutomationStatus {ACTIVE, TESTING, PAUSED}`, `TestModeResult` with
`simulatedActions` + `TestResultFeedback {PENDING, CORRECT, INCORRECT}`, CATEGORIZE confidence
threshold, executor dry-run/SIMULATED branching, audit log, category embeddings (pgvector with
positive/negative examples).

**Sub-parts (suggested order):**
- **3a — Supervised / co-pilot execution mode + approval inbox.** A mode between TESTING and ACTIVE:
  the flow runs for real, but risky/irreversible actions are **parked as PENDING_APPROVAL** instead of
  executed. The user gets an approval inbox ("classified as Complaint 82% → wants to reply with
  template Y → Approve / Edit / Reject"). Safe, reversible actions (LABEL, internal move) can still
  auto-run. Implementation mirrors the existing SIMULATED branch: add a `PENDING_APPROVAL` result + a
  `PendingAction` entity (automation, email, node, proposed-action JSON, AI confidence, status); on
  approve, execute the parked payload through the same executor path.
- **3b — Confidence-gated execution.** Generalize the CATEGORIZE threshold: auto-execute when AI
  confidence ≥ X, otherwise route to the approval inbox. Autonomy where the AI is sure, safety where
  it isn't.
- **3c — Feedback → learning loop.** Today CORRECT/INCORRECT is recorded but unused. Feed corrections
  back: a corrected CATEGORIZE result becomes a positive example for the right category (and/or
  negative for the wrong one), re-tuning that category's embedding. The "it gets better" promise.
- **3d — Production activity/audit + reversibility.** Per-automation activity feed for ACTIVE runs:
  every action with the AI's reasoning (category+confidence, extracted fields), outcome, timestamp;
  undo for reversible actions; irreversible actions are exactly why they go through 3a.

**Why first:** it is the trust precondition for the core value prop (AI auto-handling real customer
email). Without it, even with payment + OAuth, businesses won't enable auto-replies. And it is
buildable now — no external vendor — reusing the TESTING/feedback/threshold/executor infrastructure.
