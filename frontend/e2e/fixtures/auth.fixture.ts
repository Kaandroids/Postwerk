import { Page } from '@playwright/test';

const FAKE_ACCESS_TOKEN =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiaWF0IjoxNzE2MDAwMDAwLCJleHAiOjk5OTk5OTk5OTl9.fake';

const FAKE_REFRESH_TOKEN =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwidHlwZSI6InJlZnJlc2giLCJpYXQiOjE3MTYwMDAwMDAsImV4cCI6OTk5OTk5OTk5OX0.fake';

export async function setAuthTokens(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem(
      'access_token',
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiaWF0IjoxNzE2MDAwMDAwLCJleHAiOjk5OTk5OTk5OTl9.fake'
    );
    localStorage.setItem(
      'refresh_token',
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwidHlwZSI6InJlZnJlc2giLCJpYXQiOjE3MTYwMDAwMDAsImV4cCI6OTk5OTk5OTk5OX0.fake'
    );
  });
}

/** Dismiss cookie consent banner by setting consent in localStorage */
export async function dismissCookieConsent(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem(
      'cookie_consent',
      JSON.stringify({
        essential: true,
        analytics: true,
        marketing: true,
        timestamp: new Date().toISOString(),
      })
    );
  });
}

export async function setAdminAuthTokens(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem(
      'access_token',
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiaWF0IjoxNzE2MDAwMDAwLCJleHAiOjk5OTk5OTk5OTl9.fake'
    );
    localStorage.setItem(
      'refresh_token',
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwidHlwZSI6InJlZnJlc2giLCJpYXQiOjE3MTYwMDAwMDAsImV4cCI6OTk5OTk5OTk5OX0.fake'
    );
    localStorage.setItem('user_role', 'ADMIN');
  });
}

export { FAKE_ACCESS_TOKEN, FAKE_REFRESH_TOKEN };
