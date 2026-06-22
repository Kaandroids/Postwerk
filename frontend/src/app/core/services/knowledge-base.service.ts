import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { ImportResult } from '../../models/import-result.model';
import {
  KbEntry,
  KbEntryRequest,
  KnowledgeBase,
  KnowledgeBaseRequest,
} from '../../models/knowledge-base.model';

/**
 * HTTP client for org-scoped knowledge bases and their entries. Unlike the simple CRUD resources,
 * a KB also owns sub-entries and a bulk CSV import, so it does not extend {@code ResourceCrudService}.
 * The active-org header is attached by the API interceptor (multi-tenant #4).
 */
@Injectable({ providedIn: 'root' })
export class KnowledgeBaseService {
  private readonly api = inject(ApiService);
  private readonly base = '/knowledge-bases';

  list() {
    return this.api.get<KnowledgeBase[]>(this.base);
  }

  get(id: string) {
    return this.api.get<KnowledgeBase>(`${this.base}/${id}`);
  }

  create(request: KnowledgeBaseRequest) {
    return this.api.post<KnowledgeBase>(this.base, request);
  }

  update(id: string, request: KnowledgeBaseRequest) {
    return this.api.put<KnowledgeBase>(`${this.base}/${id}`, request);
  }

  delete(id: string) {
    return this.api.delete<void>(`${this.base}/${id}`);
  }

  listEntries(id: string) {
    return this.api.get<KbEntry[]>(`${this.base}/${id}/entries`);
  }

  addEntry(id: string, request: KbEntryRequest) {
    return this.api.post<KbEntry>(`${this.base}/${id}/entries`, request);
  }

  updateEntry(id: string, entryId: string, request: KbEntryRequest) {
    return this.api.put<KbEntry>(`${this.base}/${id}/entries/${entryId}`, request);
  }

  deleteEntry(id: string, entryId: string) {
    return this.api.delete<void>(`${this.base}/${id}/entries/${entryId}`);
  }

  /** Bulk import entry rows (field-value maps). The KB's uniqueField drives upsert vs full replace. */
  import(id: string, rows: Record<string, unknown>[]) {
    return this.api.post<ImportResult>(`${this.base}/${id}/import`, { rows });
  }
}
