import { Injectable } from '@angular/core';
import { ResourceCrudService } from './resource-crud.service';
import { Template, TemplateExport, TemplateRequest } from '../../models/template.model';

/**
 * Manages email response templates with full CRUD operations and bulk import/export.
 */
@Injectable({ providedIn: 'root' })
export class TemplateService extends ResourceCrudService<Template, TemplateRequest, TemplateExport> {
  protected readonly basePath = '/templates';
}
