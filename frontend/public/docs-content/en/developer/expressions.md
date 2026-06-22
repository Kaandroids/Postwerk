# Variables & expressions

_Reference any value produced upstream with double-brace expressions. This is the same syntax used in every field, header, and template body._

## Syntax

Wrap a path in double braces: `{{namespace.field}}`. Namespaces are assigned per node — the email trigger owns `email.*`, while each processing node owns its own namespace (some numbered by node, like `extraction_0` or `http_<id>`).

The variable picker in the inspector lists every value available to the selected node, so you rarely need to type a path by hand.

## The email namespace

A Trigger in **Email** mode seeds the `email.*` namespace for the whole run:

| Expression | Type | Description |
|---|---|---|
| `{{email.from}}` | string | Sender address |
| `{{email.fromName}}` | string | Sender display name |
| `{{email.to}}` | string | Recipient address |
| `{{email.cc}}` | string | Cc recipients |
| `{{email.subject}}` | string | Subject line |
| `{{email.body}}` | string | Full text body |
| `{{email.receivedAt}}` | datetime | Receipt time (ISO 8601) |
| `{{email.hasAttachments}}` | boolean | Whether the email has attachments |
| `{{email.isReply}}` | boolean | Whether it is a reply to an existing thread |
| `{{email.folder}}` | string | Source folder |

A Trigger in **Webhook** mode instead seeds `trigger.<field>` from the inbound JSON payload (mapped by a parameter set). Schedule mode seeds no namespace.

## Node-produced variables

Each node injects its own namespace once it runs:

| Expression | From | Description |
|---|---|---|
| `{{category.name}}` | Categorize | Chosen category name |
| `{{category.confidence}}` | Categorize | Confidence, 0–1 |
| `{{extraction_0.<field>}}` | Extract | A field defined in the parameter set |
| `{{vectorsearch_0.confidence}}` | Knowledge match | Judge confidence, 0–1 |
| `{{vectorsearch_0.match.<field>}}` | Knowledge match | A field of the matched entry |
| `{{http_0.statusCode}}` | HTTP Request | HTTP status code (always set, even on errors) |
| `{{http_0.body}}` | HTTP Request | Raw response body |
| `{{integration_0.<field>}}` | Integration call | An output field of the called integration |
| `{{input.<field>}}` | Input | An input field (inside an integration) |

> [!TIP]
> The numbered suffix (`extraction_0`, `http_1`) identifies the node instance, so a flow with two Extract nodes exposes `extraction_0.*` and `extraction_1.*` independently.

## Constants

Reusable values defined on the automation are available as `{{const.NAME}}` and can hold secrets (API keys, tokens) that never appear in node config.

## Using expressions in JSON

Expressions interpolate anywhere — including a Webhook request body. Quote string values; leave numbers unquoted:

```json
{
  "name": "{{extraction_0.name}}",
  "email": "{{email.from}}",
  "amount": {{extraction_0.total}},
  "received": "{{email.receivedAt}}"
}
```

> [!DANGER]
> Email content is attacker-controlled. When you pass `{{email.body}}` or any extracted field to an external system, treat it as untrusted and validate it on the receiving end.

See the [Node Reference](/docs/nodes/overview) for the exact variables each node produces.
