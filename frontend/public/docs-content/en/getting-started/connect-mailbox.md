# Connect a mailbox

_Link the inboxes Postwerk reads from and sends through. You can connect several and target them per trigger._

## Supported providers

| Provider | Auth | Notes |
|----------|------|-------|
| Gmail / Google Workspace | OAuth | Recommended; least setup. |
| Microsoft 365 / Outlook | OAuth | Org consent may be required. |
| Generic IMAP/SMTP | App password | Host, port, and credentials. |

## Add an account

1. Go to **Email accounts → Add account**.
2. Choose your provider and complete the consent flow.
3. Pick which folders Postwerk may read.
4. Send a test message to confirm the connection.

> [!WARNING]
> Replies and Send-email actions go out from the connected address. Configure SPF/DKIM on your domain so they aren't marked as spam.

> [!INFO]
> Mailbox credentials are encrypted at rest and never returned in API responses. Access to each mailbox is granted per organization member — see [Roles & permissions](/docs/teams/roles-permissions).
