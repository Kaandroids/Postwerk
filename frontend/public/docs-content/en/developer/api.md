# Triggering automations programmatically

_Postwerk has no general REST API or API keys. To start a run from your own code, put a Trigger node in Webhook mode — it exposes a public inbound URL you can POST to._

## How it works

A [Trigger](/docs/nodes/trigger) node in **Webhook** mode publishes a public inbound URL. Anything that can make an HTTP POST — a script, another service, a no-code tool — can hit that URL with a JSON payload to start a run. A **parameter set** maps the fields of your payload into the `trigger.*` namespace, which the rest of the flow reads.

## 1 · Switch the trigger to Webhook mode

In the automation editor, select the Trigger node and choose **Webhook** as the mode. Postwerk generates the inbound URL and lets you pick the parameter set that describes the expected payload.

## 2 · Choose authentication

The inbound URL supports three auth options:

| Auth | How callers prove themselves |
|------|------------------------------|
| None | Anyone with the URL can post (treat the URL as a secret) |
| API-key header | Send a shared secret in a request header |
| HMAC signature | Sign the request body; Postwerk verifies the signature |

## 3 · POST a payload

```bash
curl -X POST "https://app.postwerk.com/api/v1/hooks/<endpoint-id>" \
  -H "Content-Type: application/json" \
  -d '{ "subject": "New order", "body": "Order #10024 for Jane Doe" }'
```

The parameter set maps each JSON field onto the `trigger.*` namespace, so downstream nodes can read `{{trigger.subject}}`, `{{trigger.body}}`, and so on.

> [!TIP]
> Webhook-mode trigger data lands in `trigger.*` (not `email.*`). When testing, the test input form switches to a JSON payload editor for webhook triggers — see [Test cases & assertions](/docs/testing/test-cases).

> [!DANGER]
> The payload is attacker-controlled. When passing `{{trigger.*}}` values to a downstream system, treat them as untrusted and validate on the receiving end. Prefer API-key or HMAC auth over None for anything sensitive.
