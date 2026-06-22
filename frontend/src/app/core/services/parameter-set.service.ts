import { Injectable } from '@angular/core';
import { ResourceCrudService } from './resource-crud.service';
import { ParameterSet, ParameterSetExport, ParameterSetRequest } from '../../models/parameter-set.model';

/**
 * Manages reusable parameter sets for automation nodes, with full CRUD and import/export support.
 */
@Injectable({ providedIn: 'root' })
export class ParameterSetService extends ResourceCrudService<ParameterSet, ParameterSetRequest, ParameterSetExport> {
  protected readonly basePath = '/parameter-sets';
}
