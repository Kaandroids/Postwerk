import { test, expect } from '@playwright/test';
import { ApiClient, uniqueEmail, TEST_PASSWORD } from '../helpers/api-client';

test.describe('Auth API', () => {
  let api: ApiClient;
  const email = uniqueEmail('auth');

  test.beforeEach(async ({ request }) => {
    api = new ApiClient(request);
  });

  test('register new user', async () => {
    const res = await api.register(email, TEST_PASSWORD);
    expect(res.accessToken).toBeTruthy();
    expect(res.refreshToken).toBeTruthy();
    expect(res.expiresIn).toBeGreaterThan(0);
    expect(res.role).toBe('USER');
  });

  test('login with registered credentials', async () => {
    const res = await api.login(email, TEST_PASSWORD);
    expect(res.accessToken).toBeTruthy();
    expect(res.refreshToken).toBeTruthy();
    expect(res.role).toBe('USER');
  });

  test('refresh token', async () => {
    const loginRes = await api.login(email, TEST_PASSWORD);
    const refreshRes = await api.refresh(loginRes.refreshToken);
    expect(refreshRes.accessToken).toBeTruthy();
    expect(refreshRes.accessToken).not.toBe(loginRes.accessToken);
  });

  test('login with wrong password returns 401', async ({ request }) => {
    const res = await request.post('/api/v1/auth/login', {
      data: { email, password: 'WrongP@ss999!' },
    });
    expect(res.status()).toBe(401);
  });

  test('register duplicate email returns error', async ({ request }) => {
    const res = await request.post('/api/v1/auth/register', {
      data: {
        fullName: 'Duplicate User',
        email,
        password: TEST_PASSWORD,
        termsAccepted: true,
        marketingOptIn: false,
      },
    });
    expect([400, 409]).toContain(res.status());
  });

  test('access protected endpoint without token returns 401', async ({ request }) => {
    const res = await request.get('/api/v1/email-accounts');
    expect(res.status()).toBe(401);
  });

  test('access protected endpoint with valid token returns 200', async () => {
    await api.login(email, TEST_PASSWORD);
    const accounts = await api.get('/api/v1/email-accounts');
    expect(Array.isArray(accounts)).toBe(true);
  });
});
