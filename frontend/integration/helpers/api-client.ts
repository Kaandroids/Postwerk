import { APIRequestContext, expect } from '@playwright/test';

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  role: string;
}

export class ApiClient {
  private token = '';

  constructor(private request: APIRequestContext) {}

  setToken(token: string) {
    this.token = token;
  }

  private headers() {
    const h: Record<string, string> = { 'Content-Type': 'application/json' };
    if (this.token) h['Authorization'] = `Bearer ${this.token}`;
    return h;
  }

  // ─── Auth ──────────────────────────────────────────────────────────

  async register(
    email: string,
    password: string,
    fullName = 'Integration Test User',
  ): Promise<AuthResponse> {
    const res = await this.request.post('/api/v1/auth/register', {
      headers: this.headers(),
      data: {
        fullName,
        email,
        password,
        termsAccepted: true,
        marketingOptIn: false,
      },
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    this.token = body.accessToken;
    return body;
  }

  async login(email: string, password: string): Promise<AuthResponse> {
    const res = await this.request.post('/api/v1/auth/login', {
      headers: this.headers(),
      data: { email, password },
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    this.token = body.accessToken;
    return body;
  }

  async refresh(refreshToken: string): Promise<AuthResponse> {
    const res = await this.request.post('/api/v1/auth/refresh', {
      headers: this.headers(),
      data: { refreshToken },
    });
    expect(res.status()).toBe(200);
    return res.json();
  }

  // ─── Generic HTTP ──────────────────────────────────────────────────

  async get(path: string, expectedStatus = 200) {
    const res = await this.request.get(path, { headers: this.headers() });
    expect(res.status()).toBe(expectedStatus);
    if (expectedStatus === 204) return null;
    return res.json();
  }

  async post(path: string, data: unknown, expectedStatus = 201) {
    const res = await this.request.post(path, {
      headers: this.headers(),
      data,
    });
    expect(res.status()).toBe(expectedStatus);
    if (expectedStatus === 204) return null;
    return res.json();
  }

  async put(path: string, data: unknown, expectedStatus = 200) {
    const res = await this.request.put(path, {
      headers: this.headers(),
      data,
    });
    expect(res.status()).toBe(expectedStatus);
    return res.json();
  }

  async patch(path: string, data?: unknown, expectedStatus = 200) {
    const res = await this.request.patch(path, {
      headers: this.headers(),
      data: data ?? {},
    });
    expect(res.status()).toBe(expectedStatus);
    return res.json();
  }

  async delete(path: string, expectedStatus = 204) {
    const res = await this.request.delete(path, { headers: this.headers() });
    expect(res.status()).toBe(expectedStatus);
  }

  // ─── Raw (no assertion, return response object) ────────────────────

  async raw(method: string, path: string, data?: unknown) {
    const opts: any = { headers: this.headers() };
    if (data) opts.data = data;
    switch (method.toUpperCase()) {
      case 'GET': return this.request.get(path, opts);
      case 'POST': return this.request.post(path, opts);
      case 'PUT': return this.request.put(path, opts);
      case 'PATCH': return this.request.patch(path, opts);
      case 'DELETE': return this.request.delete(path, opts);
      default: throw new Error(`Unknown method: ${method}`);
    }
  }
}

// ─── Helpers ───────────────────────────────────────────────────────────

let counter = 0;

export function uniqueEmail(prefix = 'inttest'): string {
  return `${prefix}-${Date.now()}-${++counter}@postwerk.test`;
}

export const TEST_PASSWORD = 'SecureP@ss123!';
