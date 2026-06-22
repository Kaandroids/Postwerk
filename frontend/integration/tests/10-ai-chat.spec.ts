import { test, expect } from '@playwright/test';
import { ApiClient, uniqueEmail, TEST_PASSWORD } from '../helpers/api-client';

const hasGeminiKey = !!process.env['GEMINI_API_KEY'];

test.describe('AI Chat API', () => {
  test.skip(!hasGeminiKey, 'Skipped — no GEMINI_API_KEY in env');

  let api: ApiClient;
  let conversationId: string;

  test.beforeAll(async ({ request }) => {
    api = new ApiClient(request);
    await api.register(uniqueEmail('aichat'), TEST_PASSWORD);
  });

  test('send chat message (non-streaming)', async () => {
    const res = await api.post(
      '/api/v1/ai/chat',
      {
        message: 'List my categories',
      },
      200,
    );
    expect(res.conversationId).toBeTruthy();
    expect(res.reply).toBeTruthy();
    expect(res.phase).toBeDefined();
    conversationId = res.conversationId;
  });

  test('continue conversation', async () => {
    test.skip(!conversationId, 'No conversation from previous test');

    const res = await api.post(
      '/api/v1/ai/chat',
      {
        message: 'Now list my templates',
        conversationId,
      },
      200,
    );
    expect(res.conversationId).toBe(conversationId);
    expect(res.reply).toBeTruthy();
  });

  test('list conversations', async () => {
    const list = await api.get('/api/v1/ai/conversations');
    expect(Array.isArray(list)).toBe(true);
    expect(list.length).toBeGreaterThanOrEqual(1);
  });

  test('get conversation detail', async () => {
    test.skip(!conversationId, 'No conversation');

    const detail = await api.get(`/api/v1/ai/conversations/${conversationId}`);
    expect(detail.id).toBe(conversationId);
    expect(detail.messages).toBeDefined();
    expect(detail.messages.length).toBeGreaterThanOrEqual(2);
  });

  test('delete conversation', async () => {
    test.skip(!conversationId, 'No conversation');

    await api.delete(`/api/v1/ai/conversations/${conversationId}`);
  });
});
