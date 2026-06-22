import { HttpParams } from '@angular/common/http';

/**
 * Builds pageable list query params: always sets {@code page} + {@code size}, then appends each
 * defined, non-empty filter. The filter object's keys must match the backend query-param names.
 * Replaces the repeated `new HttpParams().set('page',…).set('size',…)` + per-filter `if` blocks
 * duplicated across the admin service clients.
 */
export function pageParams<T extends object>(filters: T, page: number, size: number): HttpParams {
  let params = new HttpParams().set('page', page).set('size', size);
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== null && value !== '') {
      params = params.set(key, value as string | number | boolean);
    }
  }
  return params;
}
