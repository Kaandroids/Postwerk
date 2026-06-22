# Publishing & installing

_Share an automation with the community, or install one and make it your own. An install always works because installing recreates everything the automation needs inside your own account._

## Installing

Browse the Marketplace, open a listing, and click **Install**. Installing **deep-copies** the automation and **clones every referenced resource** — categories, parameter sets, templates, webhook endpoints, and knowledge bases — into your own account. Because you own the copy, runs use your inbox, your quota, and your SMTP, so execution just works.

There are two kinds of listing:

- **Public listing** → you get a fully **editable copy**, opened in the normal editor. Tweak any node.
- **Private listing** → you get a **content-hidden, locked copy**. All constants are copied so the logic runs, but you can never read the internals. You configure only the values the author marked publishable, via the **Configure** surface, then bind a mailbox and activate.

## Payment

Pricing is **metadata only**. A listing can declare a pricing model and price, and acquiring it creates an entitlement — but nothing is ever charged.

## Sharing knowledge base data

When you publish an automation that references a knowledge base, you choose how much of it travels:

| Policy | What ships |
|--------|------------|
| Schema only (default) | The KB schema; buyers fill or import their own entries |
| Full | Schema plus entries, copied verbatim |
| Private listing | KB is hidden — runs, but entries aren't readable |

> [!DANGER]
> The default is schema-only for a reason. Only choose **Full** when the KB contents are meant to be public, never for proprietary or personal data.
