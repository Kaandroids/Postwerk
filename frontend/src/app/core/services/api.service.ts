import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';

/**
 * HTTP client wrapper that prefixes all requests with the API base URL.
 *
 * Provides typed convenience methods for GET, POST, PUT, PATCH, DELETE, and blob downloads.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  get<T>(path: string, options?: { params?: HttpParams }) {
    return this.http.get<T>(`${this.baseUrl}${path}`, { params: options?.params });
  }

  post<T>(path: string, body: unknown) {
    return this.http.post<T>(`${this.baseUrl}${path}`, body);
  }

  put<T>(path: string, body: unknown) {
    return this.http.put<T>(`${this.baseUrl}${path}`, body);
  }

  patch<T>(path: string, body: unknown) {
    return this.http.patch<T>(`${this.baseUrl}${path}`, body);
  }

  delete<T>(path: string) {
    return this.http.delete<T>(`${this.baseUrl}${path}`);
  }

  getBlob(path: string, options?: { params?: HttpParams }) {
    return this.http.get(`${this.baseUrl}${path}`, { responseType: 'blob', observe: 'response', params: options?.params });
  }
}
