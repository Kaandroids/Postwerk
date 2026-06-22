# Test cases & assertions

_Pin real or sample emails as test cases, replay them through the graph as a dry run, and assert on what each node produced — so a change never silently breaks routing._

## Tests vs Simulations

Postwerk gives you two ways to verify an automation:

- **Tests** — a manual **dry run** with assertions on node output. You provide the input and your expected outcomes; nothing is ever sent.
- **Simulations** — **live feedback** that populates only when an automation's status is set to TESTING and real mail arrives. You watch how real messages would have been handled, still without performing any action.

## Create a test case

In the **Tests** tab, add a case from a pasted email or one captured from a real run. Each case stores the input and your expected outcomes. The input form is aware of the trigger mode — Email triggers ask for email fields, Webhook triggers take a JSON payload, and integrations take their INPUT parameter-set fields.

## Assertions

Assert on any node's output — the branch a run took, an extracted field, a categorization, a webhook status:

| Field check | Example |
|-------------|---------|
| Branch reached | Categorize → Support |
| Category | `category.name` = Support |
| Extracted value | `extraction_0.order_no` = 10024 |
| Match field | `vectorsearch_0.match.konto_nr` = 0490 |
| HTTP status | `http_0.statusCode` = 201 |

## Mocking external and AI nodes

In a dry run, external and AI nodes are **mockable** so you can iterate offline and exercise both the happy path and the error branch:

- [Webhook](/docs/nodes/webhook) and [Integration call](/docs/nodes/integration_call) — supply a fake status code and body (or force an error).
- [Categorize](/docs/nodes/categorize), [Extract](/docs/nodes/extract), and [Knowledge match](/docs/nodes/vector_search) — supply the result you want the node to return.

Actions like reply, send, and move are **simulated** — never really executed. Each external-call node also has a per-node **live / mock** switch: leave it on mock to use your fake response, or flip it to live to make the real call during the dry run.

> [!WARNING]
> Live mode performs real side effects. It is fine for outbound webhooks, but a live Send-email or Email-action node will actually send mail to real recipients. These stay on mock by default — only opt in deliberately.
