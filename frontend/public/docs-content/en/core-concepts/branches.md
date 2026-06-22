# Branches & handles

_Decision nodes split a run into branches. Knowing which handle fires when keeps your flows predictable._

Every node exposes its own set of output **handles**. Some have a single output; others split the run by outcome or by category. Wire each handle to the path that case should take.

## Handles by node

| Node | Output handles |
|---|---|
| [Trigger](/docs/nodes/trigger) (Email mode) | `new-email`, `reply` |
| [Trigger](/docs/nodes/trigger) (Webhook / Schedule) | `output` |
| [Filter](/docs/nodes/filter) | one `check_<i>` per check (labeled with the check name) + `fallback` |
| [Categorize](/docs/nodes/categorize) | one handle per category + `uncategorized` |
| [Extract](/docs/nodes/extract) | one `extraction_<i>` per extraction entry (no fail handle) |
| [Knowledge match](/docs/nodes/vector_search) | `success`, `fail` |
| [Webhook](/docs/nodes/webhook) | `success`, `failure` |
| [Integration call](/docs/nodes/integration_call) | `done`, `failure` |
| [Email action](/docs/nodes/email_action), [Send email](/docs/nodes/send_email), [Add label](/docs/nodes/label), [Remove label](/docs/nodes/remove_label), [Delay](/docs/nodes/delay) | a single `output` handle |

## Filter checks and fallback

A [Filter](/docs/nodes/filter) node defines one or more named checks. Each check gets its own `check_<i>` handle, labeled with the check name, so you can route every condition somewhere different. Mail that matches no check leaves through `fallback`.

## Category handles

The [Categorize](/docs/nodes/categorize) node grows one output per category. Wire each to the path that kind of mail should take. Mail that doesn't clear the confidence threshold leaves through `uncategorized`.

## Success vs fail

For [Knowledge match](/docs/nodes/vector_search), [Webhook](/docs/nodes/webhook), and [Integration call](/docs/nodes/integration_call), one handle means "it worked" and the other means "it didn't" — but **fail is not the same as error**. A confident-but-negative outcome (a no-match, a non-2xx response) and a hard error both leave through the fail-side handle.

> [!WARNING]
> For Knowledge match a no-match and a hard error both route to `fail`, yet the run trace records the status distinctly (`NOT_MATCHED` vs `ERROR`). Inspect the trace when debugging which one actually happened.

## Dangling handles

A handle with nothing connected simply ends that branch — that's fine for outcomes you don't care about. What's not fine is a node with no incoming edge: the validator flags such **dangling required inputs** and blocks activation and publishing until you wire them up.
