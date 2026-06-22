# Quickstart: your first automation

_The fastest way to ship an automation in Postwerk is to describe it in plain language — the AI assistant builds the whole thing for you. Prefer to build by hand? That works too._

## The fastest way: just ask the assistant

Open the **AI assistant** and describe the job in your own words — for example:

> "Classify incoming support mail, reply to customers with an acknowledgement, and forward anything about invoices to accounting."

That is all it needs. The assistant can do **everything**, end to end:

- reads your connected mailboxes, categories, templates and knowledge bases;
- lays out the trigger, decision and action nodes and wires the branches;
- validates the result against the same rules as the editor and fixes problems;
- explains what it built, so you can accept, tweak, or refine it.

> [!TIP]
> You never have to touch the canvas. Just keep chatting — "also send me a Slack message when it's urgent" — and the assistant adjusts the automation for you. See [Building with the AI assistant](/docs/ai-assistant/building-with-ai).

When it is done, the automation is a normal graph: open it in the editor if you want to fine-tune anything, then switch it on.

## Or build it by hand

Prefer full control? Build the same flow on the canvas in five steps.

### 1 · Connect a mailbox

Open **Email accounts** and link the inbox over IMAP/OAuth. See [Connect a mailbox](/docs/getting-started/connect-mailbox).

### 2 · Add a trigger

Create a new automation, add a [Trigger](/docs/nodes/trigger) in Email mode, and pick your mailbox. Every incoming message now starts a run and exposes `{{email.subject}}`, `{{email.body}}` and more.

### 3 · Categorize the mail

Add a [Categorize](/docs/nodes/categorize) node, set the source to `{{email.body}}`, define categories — _Support_, _Sales_, _Billing_ — and a confidence threshold of 70.

> [!INFO]
> Categorize grows one output handle per category, plus an `uncategorized` handle for mail below the threshold. The choice is available downstream as `{{category.name}}` and `{{category.confidence}}`.

### 4 · Reply automatically

Off the _Support_ branch, add an [Email action](/docs/nodes/email_action) in Reply mode with a short templated body that greets `{{email.fromName}}`.

### 5 · Test before going live

Open the **Tests** tab, paste a sample email, run it as a dry run, and assert that it reaches the _Support_ branch. When it is green, switch the automation on. See [Test cases & assertions](/docs/testing/test-cases).
