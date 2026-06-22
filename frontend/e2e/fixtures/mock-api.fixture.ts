import { Page, Route } from '@playwright/test';

interface MockRoute {
  method: string;
  pattern: string | RegExp;
  handler: (route: Route) => Promise<void>;
}

export class MockApi {
  private routes: MockRoute[] = [];

  get(pattern: string | RegExp, body: unknown, status = 200): this {
    return this.addRoute('GET', pattern, body, status);
  }

  post(pattern: string | RegExp, body: unknown, status = 200): this {
    return this.addRoute('POST', pattern, body, status);
  }

  put(pattern: string | RegExp, body: unknown, status = 200): this {
    return this.addRoute('PUT', pattern, body, status);
  }

  patch(pattern: string | RegExp, body: unknown, status = 200): this {
    return this.addRoute('PATCH', pattern, body, status);
  }

  delete(pattern: string | RegExp, body: unknown = {}, status = 200): this {
    return this.addRoute('DELETE', pattern, body, status);
  }

  /** Add a route that returns a dynamic response based on the request */
  handle(
    method: string,
    pattern: string | RegExp,
    handler: (route: Route) => Promise<void>
  ): this {
    this.routes.push({ method: method.toUpperCase(), pattern, handler });
    return this;
  }

  async apply(page: Page): Promise<void> {
    // Single catch-all route that checks all registered mocks
    // Routes registered later take priority (LIFO) in Playwright
    await page.route('**/api/v1/**', async (route) => {
      const request = route.request();
      const method = request.method();
      const url = request.url();

      for (const mock of this.routes) {
        if (mock.method !== method) continue;

        const matches =
          typeof mock.pattern === 'string'
            ? url.includes(mock.pattern)
            : mock.pattern.test(url);

        if (matches) {
          await mock.handler(route);
          return;
        }
      }

      // No match — let it fall through to previously registered handlers
      await route.fallback();
    });
  }

  private addRoute(
    method: string,
    pattern: string | RegExp,
    body: unknown,
    status: number
  ): this {
    this.routes.push({
      method,
      pattern,
      handler: async (route: Route) => {
        await route.fulfill({
          status,
          contentType: 'application/json',
          body: JSON.stringify(body),
        });
      },
    });
    return this;
  }
}
