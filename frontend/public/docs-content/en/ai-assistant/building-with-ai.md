# Building with the AI assistant

_Describe the workflow you want in plain language. The assistant asks clarifying questions, lays out the nodes, wires the branches, and explains what it built._

## How it works

Open the assistant and describe the job — "forward invoices to accounting and reply to everyone else." It reads your connected mailboxes, categories, and knowledge bases, then proposes a complete automation you can accept, edit, or refine.

The assistant works in phases: it first **plans** the automation with you, and once you confirm, it switches to **building** — adding nodes, wiring branches, and filling in configuration.

## Writing a good prompt

- Name the **trigger**: which mailbox or schedule starts it.
- Describe the **decisions**: how mail should be split.
- State the **actions**: reply, forward, label, call an API.
- Mention **data** you need extracted or matched.

> [!TIP]
> The assistant validates each draft against the same rules as the editor and fixes problems — nodes with no incoming edge, missing categories — before handing it to you.

## After it builds

Every assistant-built automation is a normal graph: open it in the editor, tweak any node, and [test](/docs/testing/test-cases) it before going live.
