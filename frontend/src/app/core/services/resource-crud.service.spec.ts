import { Injectable } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ResourceCrudService } from './resource-crud.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

interface Thing { id: string; name: string; }
interface ThingReq { name: string; }

/** A minimal concrete subclass that only declares basePath — the eight endpoints are inherited. */
@Injectable()
class ThingService extends ResourceCrudService<Thing, ThingReq> {
  protected readonly basePath = '/things';
}

describe('ResourceCrudService (generic CRUD base)', () => {
  let api: MockApi;
  let service: ThingService;

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [ThingService, provideMockApi(api)] });
    service = TestBed.inject(ThingService);
  });

  it('list() GETs the base path', () => {
    service.list();
    expect(api.get).toHaveBeenCalledWith('/things');
  });

  it('get() GETs by id', () => {
    service.get('1');
    expect(api.get).toHaveBeenCalledWith('/things/1');
  });

  it('create() POSTs the request', () => {
    const r = { name: 'a' };
    service.create(r);
    expect(api.post).toHaveBeenCalledWith('/things', r);
  });

  it('update() PUTs to the id path', () => {
    const r = { name: 'b' };
    service.update('1', r);
    expect(api.put).toHaveBeenCalledWith('/things/1', r);
  });

  it('delete() DELETEs by id', () => {
    service.delete('1');
    expect(api.delete).toHaveBeenCalledWith('/things/1');
  });

  it('toggleLock() PATCHes the lock sub-path', () => {
    service.toggleLock('1');
    expect(api.patch).toHaveBeenCalledWith('/things/1/lock', {});
  });

  it('export() GETs the export sub-path', () => {
    service.export();
    expect(api.get).toHaveBeenCalledWith('/things/export');
  });

  it('import() POSTs the rows to the import sub-path', () => {
    const data = [{ id: '1', name: 'a' }];
    service.import(data);
    expect(api.post).toHaveBeenCalledWith('/things/import', data);
  });
});
