import { test, expect } from '../../fixtures/test-fixtures';
import { dismissCookieConsent } from '../../fixtures/auth.fixture';
import { mockRefreshResponse } from '../../mocks';
import { MockApi } from '../../fixtures/mock-api.fixture';

test.describe('Token Refresh', () => {
  test('should refresh token on 401 and retry the request', async ({ page }) => {
    await dismissCookieConsent(page);
    const api = new MockApi();
    let callCount = 0;

    // First call to /users/me returns 401, second returns success
    api.handle('GET', '/api/v1/users/me', async (route) => {
      callCount++;
      if (callCount === 1) {
        await route.fulfill({ status: 401, contentType: 'application/json', body: '{}' });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ id: 1, email: 'test@example.com', fullName: 'Test User' }),
        });
      }
    });

    api
      .post('/api/v1/auth/refresh', mockRefreshResponse)
      .get('/api/v1/email-accounts', [])
      .get(/\/folders/, []);

    await api.apply(page);

    // Set tokens that will trigger a 401
    await page.addInitScript(() => {
      localStorage.setItem('access_token', 'expired-token');
      localStorage.setItem('refresh_token', 'valid-refresh-token');
    });

    await page.goto('/dashboard');
    // Should stay on dashboard after successful refresh
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('should redirect to login when refresh fails', async ({ page }) => {
    await dismissCookieConsent(page);
    const api = new MockApi();
    api
      .get('/api/v1/users/me', {}, 401)
      .post('/api/v1/auth/refresh', { message: 'Invalid refresh token' }, 401)
      .get('/api/v1/email-accounts', [])
      .get(/\/folders/, []);

    await api.apply(page);

    await page.addInitScript(() => {
      localStorage.setItem('access_token', 'expired-token');
      localStorage.setItem('refresh_token', 'invalid-refresh-token');
    });

    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/auth\/login/);
  });
});
