import { of } from 'rxjs';
import { vi } from 'vitest';
import { ApiService } from '../app/core/services/api.service';

/**
 * A Vitest mock of {@link ApiService}: every HTTP verb is a spy that returns an empty
 * observable by default. Most {@code core/services} are thin wrappers over {@link ApiService}
 * (they never touch {@link HttpClient} directly), so a unit test only needs to assert which
 * verb was called with which path/body — no HTTP machinery required.
 *
 * <p>Set a per-call return value with {@code mockApi.get.mockReturnValue(of(data))} and assert
 * the call with {@code expect(mockApi.post).toHaveBeenCalledWith('/path', body)}.</p>
 *
 * @example
 *   const api = createMockApi();
 *   TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
 *   const service = TestBed.inject(MyService);
 */
export interface MockApi {
  get: ReturnType<typeof vi.fn>;
  post: ReturnType<typeof vi.fn>;
  put: ReturnType<typeof vi.fn>;
  patch: ReturnType<typeof vi.fn>;
  delete: ReturnType<typeof vi.fn>;
  getBlob: ReturnType<typeof vi.fn>;
}

/** Builds a fresh {@link MockApi} with every verb returning {@code of(undefined)}. */
export function createMockApi(): MockApi {
  return {
    get: vi.fn(() => of(undefined)),
    post: vi.fn(() => of(undefined)),
    put: vi.fn(() => of(undefined)),
    patch: vi.fn(() => of(undefined)),
    delete: vi.fn(() => of(undefined)),
    getBlob: vi.fn(() => of(undefined)),
  };
}

/** Provider entry wiring a {@link MockApi} in place of the real {@link ApiService}. */
export function provideMockApi(api: MockApi) {
  return { provide: ApiService, useValue: api };
}
