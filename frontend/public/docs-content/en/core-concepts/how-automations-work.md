# How automations work

_Every automation is a directed graph. Once triggers, nodes, handles, and variables click into place, you have most of what you need to build confidently._

## The run

Every automation has exactly one **Trigger**. When it fires, it creates a **run** — a single pass through the graph. The run carries a shared **context**: the incoming email plus every variable produced along the way. From the trigger, the run walks node by node, following the handles you wired, until it reaches the end of each branch it enters.

## Nodes

A node does one job — filter, classify, extract, label, send, call a webhook, wait. Each node reads what it needs from the shared run context and may write new variables back to it, so a later node can build on an earlier node's work. See the full set in the [Node Reference](/docs/nodes/overview).

## Handles & branches

Nodes connect through typed **handles**. A plain action node has a single output, while decision nodes expose several — [Categorize](/docs/nodes/categorize) grows one handle per category plus an `uncategorized` handle, and [Filter](/docs/nodes/filter) grows one handle per check plus a `fallback`. Read more in [Branches & handles](/docs/core-concepts/branches).

### Port types

Handles are colored by the data type that flows through them, so a mismatched connection is visible on the canvas.

| Type | Carries |
|---|---|
| email | A full message context |
| param | Extracted fields |
| cat | A categorization result |
| json | A webhook / knowledge-base response |
| any | Untyped pass-through |

## Variables

Anything a node produces becomes a `{{variable}}` you can reference downstream. Each node owns a namespace — the Email-mode trigger owns `email.*`, [Categorize](/docs/nodes/categorize) owns `category.*`, and each Extract, HTTP Request, or Integration instance owns a namespace keyed by its node id (`extraction_0`, `http_0`, …).

| Variable | From | Description |
|---|---|---|
| `{{email.from}}`, `{{email.subject}}`, `{{email.body}}`, … | [Trigger](/docs/nodes/trigger) (Email) | The incoming message |
| `{{trigger.<field>}}` | [Trigger](/docs/nodes/trigger) (Webhook) | Inbound payload fields |
| `{{category.name}}`, `{{category.confidence}}` | [Categorize](/docs/nodes/categorize) | Chosen category |
| `{{extraction_0.<field>}}` | [Extract](/docs/nodes/extract) | Extracted fields |
| `{{vectorsearch_0.confidence}}`, `{{vectorsearch_0.match.<field>}}` | [Knowledge match](/docs/nodes/vector_search) | Matched entry |
| `{{http_0.statusCode}}`, `{{http_0.body}}` | [HTTP Request](/docs/nodes/webhook) | HTTP response (status always set) |
| `{{integration_0.<field>}}` | [Integration call](/docs/nodes/integration_call) | Integration return |

For the full syntax — defaults, conditionals, and formatting — see [Variables & expressions](/docs/developer/expressions).

> [!INFO]
> The editor saves continuously. Toggling an automation on or off is the only switch between draft and live.
