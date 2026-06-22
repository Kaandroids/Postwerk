import { inject } from '@angular/core';
import { ApiService } from './api.service';
import { ImportResult } from '../../models/import-result.model';

/**
 * Generic CRUD + lock + bulk import/export HTTP client for an org-scoped resource.
 *
 * Concrete services only declare {@link basePath} and the type parameters; the eight
 * endpoints (list/get/create/update/delete/toggleLock/export/import) are inherited.
 * This removes the byte-for-byte duplication previously repeated across
 * {@code CategoryService}, {@code TemplateService}, and {@code ParameterSetService}.
 *
 * @typeParam T   the resource response type (must carry an {@code id})
 * @typeParam Req the create/update request body type
 * @typeParam Exp the export/import row type (defaults to {@code T})
 */
export abstract class ResourceCrudService<T extends { id: string }, Req, Exp = T> {
  protected readonly api = inject(ApiService);

  /** API path prefix for this resource, e.g. {@code '/categories'}. */
  protected abstract readonly basePath: string;

  list() {
    return this.api.get<T[]>(this.basePath);
  }

  get(id: string) {
    return this.api.get<T>(`${this.basePath}/${id}`);
  }

  create(request: Req) {
    return this.api.post<T>(this.basePath, request);
  }

  update(id: string, request: Req) {
    return this.api.put<T>(`${this.basePath}/${id}`, request);
  }

  delete(id: string) {
    return this.api.delete<void>(`${this.basePath}/${id}`);
  }

  toggleLock(id: string) {
    return this.api.patch<T>(`${this.basePath}/${id}/lock`, {});
  }

  export() {
    return this.api.get<Exp[]>(`${this.basePath}/export`);
  }

  import(data: Exp[]) {
    return this.api.post<ImportResult>(`${this.basePath}/import`, data);
  }
}
