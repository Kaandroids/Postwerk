/** Mock inbound webhook endpoint, matching WebhookEndpointResponse. */
export const mockWebhookEndpoint = {
  id: 'wep-1',
  automationId: '1',
  nodeId: 'trigger-1',
  token: 'tok_abc123',
  url: 'https://app.postwerk.test/api/v1/hooks/tok_abc123',
  authMode: 'NONE' as const,
  authHeaderName: null,
  hasSecret: false,
  active: true,
  triggerCount: 0,
  lastTriggeredAt: null,
};

/** Endpoint after token regeneration (new token + URL). */
export const mockWebhookEndpointRegenerated = {
  ...mockWebhookEndpoint,
  token: 'tok_xyz789',
  url: 'https://app.postwerk.test/api/v1/hooks/tok_xyz789',
};

/** Endpoint configured with API_KEY auth. */
export const mockWebhookEndpointApiKey = {
  ...mockWebhookEndpoint,
  authMode: 'API_KEY' as const,
  authHeaderName: 'X-API-Key',
};

/** Endpoint configured with HMAC auth and a stored secret. */
export const mockWebhookEndpointHmac = {
  ...mockWebhookEndpoint,
  authMode: 'HMAC' as const,
  hasSecret: true,
};

export const mockGeneratedSecret = { secret: 'whsec_generated_secret_value' };
