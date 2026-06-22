# Frequently asked questions

_Quick answers to the things people ask most._

## Does Postwerk read all my email?

Only mail in the folders you authorize, and only to run your automations. You control which mailboxes and folders are connected.

## What counts against my quota?

The quota is **cost-based and applies to AI usage only**. Each AI node call (Categorize, Extract, Knowledge match) has a cost that counts toward your plan's monthly cap. Non-AI processing — filters, labels, replies, webhooks, delays — never counts against it. See the table below for plan limits.

## Which AI models are used?

The AI nodes — Categorize, Extract, and Knowledge match — are powered by Google's **Gemini** models. There is no user-facing model picker and nothing to configure; Postwerk handles it for you.

## What are the plans?

| Plan | AI quota |
|------|----------|
| STARTER | AI disabled |
| PRO | €5 / month cost cap on AI usage |
| ENTERPRISE | Unlimited |

The cap is on cost, not raw call count — each AI call's cost is tracked and counts toward the monthly limit.

## Can I test without sending real email?

Yes. **Tests** run as a dry run with all actions simulated — nothing is sent and no webhook is called unless you explicitly switch a node to live. **Simulations** give live feedback when an automation's status is TESTING, again without performing actions. See [Test cases & assertions](/docs/testing/test-cases).

## Where is my data stored?

Data is org-scoped and processed in the EU. Knowledge bases never cross organization boundaries.

## How is access controlled?

Everything belongs to an organization. You invite members, grant per-mailbox read and send permissions, and the role enum is simply USER or ADMIN. See [Roles & permissions](/docs/teams/roles-permissions).

## Can I call automations from my own code?

Yes. Put a Trigger node in Webhook mode to expose a public inbound URL you can POST a JSON payload to. See [Triggering automations programmatically](/docs/developer/api).

## How do I get help?

Use the in-app help on any node, or contact support from the assistant. Admins also get priority support on paid plans.
