import { Injectable } from '@angular/core';
import { ResourceCrudService } from './resource-crud.service';
import { Category, CategoryExport, CategoryRequest } from '../../models/category.model';

/**
 * Manages email classification categories with full CRUD support and bulk import/export.
 */
@Injectable({ providedIn: 'root' })
export class CategoryService extends ResourceCrudService<Category, CategoryRequest, CategoryExport> {
  protected readonly basePath = '/categories';
}
