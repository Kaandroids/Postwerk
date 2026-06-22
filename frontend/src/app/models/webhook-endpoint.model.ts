export type WebhookAuthMode = 'NONE' | 'API_KEY' | 'HMAC';

/** Inbound webhook endpoint backing a TRIGGER node in WEBHOOK mode. */
export interface WebhookEndpoint {
  id: string;
  automationId: string;
  nodeId: string;
  token: string;
  url: string;
  authMode: WebhookAuthMode;
  authHeaderName: string | null;
  hasSecret: boolean;
  active: boolean;
  triggerCount: number;
  lastTriggeredAt: string | null;
}

/** Request to configure inbound webhook authentication. */
export interface WebhookAuthRequest {
  authMode: WebhookAuthMode;
  authHeaderName?: string | null;
  signingSecret?: string | null;
}

/** One-time response carrying a freshly generated signing secret. */
export interface GeneratedSecret {
  secret: string;
}
