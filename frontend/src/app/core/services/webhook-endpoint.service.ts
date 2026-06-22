import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { GeneratedSecret, WebhookAuthRequest, WebhookEndpoint } from '../../models/webhook-endpoint.model';

/** Manages inbound webhook endpoints (URL, token rotation, auth) for automation TRIGGER nodes. */
@Injectable({ providedIn: 'root' })
export class WebhookEndpointService {
  private api = inject(ApiService);

  get(id: string): Observable<WebhookEndpoint> {
    return this.api.get<WebhookEndpoint>(`/webhook-endpoints/${id}`);
  }

  regenerateToken(id: string): Observable<WebhookEndpoint> {
    return this.api.post<WebhookEndpoint>(`/webhook-endpoints/${id}/regenerate-token`, {});
  }

  setAuth(id: string, request: WebhookAuthRequest): Observable<WebhookEndpoint> {
    return this.api.put<WebhookEndpoint>(`/webhook-endpoints/${id}/auth`, request);
  }

  generateSecret(id: string): Observable<GeneratedSecret> {
    return this.api.post<GeneratedSecret>(`/webhook-endpoints/${id}/generate-secret`, {});
  }
}
