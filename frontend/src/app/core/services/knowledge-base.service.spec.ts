import { TestBed } from '@angular/core/testing';
import { KnowledgeBaseService } from './knowledge-base.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('KnowledgeBaseService', () => {
  let api: MockApi;
  let service: KnowledgeBaseService;
  const base = '/knowledge-bases';

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(KnowledgeBaseService);
  });

  it('list()/get() hit the KB collection and item', () => {
    service.list();
    expect(api.get).toHaveBeenCalledWith(base);
    service.get('k1');
    expect(api.get).toHaveBeenCalledWith(`${base}/k1`);
  });

  it('create() POSTs, update() PUTs, delete() DELETEs the KB', () => {
    const req = { name: 'KB' } as never;
    service.create(req);
    expect(api.post).toHaveBeenCalledWith(base, req);
    service.update('k1', req);
    expect(api.put).toHaveBeenCalledWith(`${base}/k1`, req);
    service.delete('k1');
    expect(api.delete).toHaveBeenCalledWith(`${base}/k1`);
  });

  it('listEntries()/addEntry() hit the entries sub-collection', () => {
    service.listEntries('k1');
    expect(api.get).toHaveBeenCalledWith(`${base}/k1/entries`);
    const entry = { fields: {} } as never;
    service.addEntry('k1', entry);
    expect(api.post).toHaveBeenCalledWith(`${base}/k1/entries`, entry);
  });

  it('updateEntry() PUTs and deleteEntry() DELETEs a single entry', () => {
    const entry = { fields: {} } as never;
    service.updateEntry('k1', 'e1', entry);
    expect(api.put).toHaveBeenCalledWith(`${base}/k1/entries/e1`, entry);
    service.deleteEntry('k1', 'e1');
    expect(api.delete).toHaveBeenCalledWith(`${base}/k1/entries/e1`);
  });

  it('import() POSTs rows wrapped in a payload', () => {
    const rows = [{ a: 1 }, { a: 2 }];
    service.import('k1', rows);
    expect(api.post).toHaveBeenCalledWith(`${base}/k1/import`, { rows });
  });
});
