import { TestBed } from '@angular/core/testing';
import { HttpParams } from '@angular/common/http';
import { EmailService } from './email.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('EmailService', () => {
  let api: MockApi;
  let service: EmailService;
  const acc = 'acc1';
  const base = `/email-accounts/${acc}/emails`;

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(EmailService);
  });

  it('list() GETs the account email path with no params by default', () => {
    service.list(acc);
    const [path, opts] = api.get.mock.calls[0];
    expect(path).toBe(base);
    expect((opts.params as HttpParams).keys()).toEqual([]);
  });

  it('list() maps all filter options onto query params', () => {
    service.list(acc, {
      page: 2, size: 10, folder: 'INBOX', query: 'hi', isRead: false,
      dateFrom: '2026-01-01', dateTo: '2026-02-01', categoryId: 'c1', processed: true, automationId: 'a1',
    });
    const p = api.get.mock.calls[0][1].params as HttpParams;
    expect(p.get('page')).toBe('2');
    expect(p.get('size')).toBe('10');
    expect(p.get('folder')).toBe('INBOX');
    expect(p.get('query')).toBe('hi');
    expect(p.get('isRead')).toBe('false');
    expect(p.get('dateFrom')).toBe('2026-01-01');
    expect(p.get('dateTo')).toBe('2026-02-01');
    expect(p.get('categoryId')).toBe('c1');
    expect(p.get('processed')).toBe('true');
    expect(p.get('automationId')).toBe('a1');
  });

  it('get() GETs a single email', () => {
    service.get(acc, 'm1');
    expect(api.get).toHaveBeenCalledWith(`${base}/m1`);
  });

  it('markRead() PATCHes the read flag', () => {
    service.markRead(acc, 'm1', true);
    expect(api.patch).toHaveBeenCalledWith(`${base}/m1/read`, { read: true });
  });

  it('toggleStar() PATCHes the star sub-path', () => {
    service.toggleStar(acc, 'm1');
    expect(api.patch).toHaveBeenCalledWith(`${base}/m1/star`, {});
  });

  it('reprocess() POSTs to the reprocess sub-path', () => {
    service.reprocess(acc, 'm1');
    expect(api.post).toHaveBeenCalledWith(`${base}/m1/reprocess`, {});
  });

  it('sync() POSTs to the sync sub-path', () => {
    service.sync(acc);
    expect(api.post).toHaveBeenCalledWith(`${base}/sync`, {});
  });

  it('downloadAttachment() GETs the attachment blob by index', () => {
    service.downloadAttachment(acc, 'm1', 3);
    expect(api.getBlob).toHaveBeenCalledWith(`${base}/m1/attachments/3`);
  });

  it('deleteEmail()/restoreEmail()/emptyTrash() hit the trash lifecycle paths', () => {
    service.deleteEmail(acc, 'm1');
    expect(api.delete).toHaveBeenCalledWith(`${base}/m1`);
    service.restoreEmail(acc, 'm1');
    expect(api.post).toHaveBeenCalledWith(`${base}/m1/restore`, {});
    service.emptyTrash(acc);
    expect(api.delete).toHaveBeenCalledWith(`${base}/trash`);
  });

  it('send()/saveDraft() POST the compose payload', () => {
    const req = { subject: 'Hi' } as never;
    service.send(acc, req);
    expect(api.post).toHaveBeenCalledWith(`${base}/send`, req);
    service.saveDraft(acc, req);
    expect(api.post).toHaveBeenCalledWith(`${base}/drafts`, req);
  });

  it('updateDraft() PUTs and deleteDraft() DELETEs the draft', () => {
    const req = { subject: 'Hi' } as never;
    service.updateDraft(acc, 'd1', req);
    expect(api.put).toHaveBeenCalledWith(`${base}/drafts/d1`, req);
    service.deleteDraft(acc, 'd1');
    expect(api.delete).toHaveBeenCalledWith(`${base}/drafts/d1`);
  });

  it('uploadAttachment() POSTs a FormData carrying the file', () => {
    const file = new File(['x'], 'a.pdf', { type: 'application/pdf' });
    service.uploadAttachment(acc, 'd1', file);
    const [path, body] = api.post.mock.calls[0];
    expect(path).toBe(`${base}/drafts/d1/attachments`);
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get('file')).toBe(file);
  });

  it('deleteAttachment()/listAttachments() hit the attachment sub-paths', () => {
    service.deleteAttachment(acc, 'd1', 'at1');
    expect(api.delete).toHaveBeenCalledWith(`${base}/drafts/d1/attachments/at1`);
    service.listAttachments(acc, 'd1');
    expect(api.get).toHaveBeenCalledWith(`${base}/drafts/d1/attachments`);
  });
});
