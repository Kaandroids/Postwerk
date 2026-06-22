import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Secret, SecretRequest } from '../../models/secret.model';

/** Manages encrypted secret CRUD operations for use in automation webhook headers and API calls. */
@Injectable({ providedIn: 'root' })
export class SecretService {
  private api = inject(ApiService);

  list(): Observable<Secret[]> {
    return this.api.get<Secret[]>('/secrets');
  }

  create(request: SecretRequest): Observable<Secret> {
    return this.api.post<Secret>('/secrets', request);
  }

  update(id: string, request: SecretRequest): Observable<Secret> {
    return this.api.put<Secret>(`/secrets/${id}`, request);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`/secrets/${id}`);
  }
}
