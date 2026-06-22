# Roles & permissions

_Postwerk is multi-tenant: everything lives inside an organization. Invite members, grant access to each mailbox, and control who can administer the org._

## Organizations own everything

An **organization** is the tenant. Automations, categories, parameter sets, templates, knowledge bases, and email accounts all belong to the org — not to an individual — so work survives member changes. Every query is scoped to the org you have active.

## Member roles

Every member has an organization role that decides what they can do:

| Role | Can |
|------|-----|
| Admin | Everything except billing, deleting the org, and transferring ownership — including managing members, plans, and all automations. Implicit access to every mailbox. |
| Member | Build, test, and activate automations and resources, decide approvals, and install from the marketplace. Mailbox access only through granted permissions. |
| Viewer | Read-only: view automations, runs, resources, and the audit log — no changes. |

> [!INFO]
> Every organization has exactly one **Owner** — the person who created it — with full control including billing and deleting the org. Ownership can be transferred, but not assigned like a normal role.

## Per-mailbox grants

Owners and admins implicitly have access to every mailbox. For **members and viewers**, access to each connected mailbox is granted **per member** as two independent permissions:

- **Read** — the member's automations may read mail from that mailbox.
- **Send** — the member may send or reply through that mailbox.

A member only sees and acts on the mailboxes they have been granted. Send permission is also enforced at activation time, so an automation can't go live sending through a mailbox the owner can't send from.

## Inviting members

1. Open **Teams → Members → Invite**.
2. Enter an email address.
3. The invitee joins your organization on accepting — no separate account is needed per org.
4. Grant the per-mailbox read/send permissions the member needs.

## Plans & AI quota

Plans set a monthly **cost cap on AI usage** — see the [FAQ](/docs/faq/general) for how the quota works. Non-AI processing never counts against it.

> [!INFO]
> Because everything is org-scoped, automations and resources belong to the organization rather than the person who created them — so they survive members joining or leaving.
