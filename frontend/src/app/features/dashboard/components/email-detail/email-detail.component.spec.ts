import { TestBed } from '@angular/core/testing';
import { DomSanitizer } from '@angular/platform-browser';
import { signal } from '@angular/core';
import { vi } from 'vitest';
import { EmailDetailComponent } from './email-detail.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { WorkspaceService } from '../../../../core/services/workspace.service';
import { EmailService } from '../../../../core/services/email.service';
import { ExportImportService } from '../../../../core/services/export-import.service';

/** Typed view of the protected members exercised here (logic-only, no template render). */
interface Access {
  linkedText(): string;
  parsedAttachments(): unknown[];
  sanitizedHtml(): string;
  traceOpen(): string | null;
  toggleTraceOpen(id: string): void;
  getNodeTypeLabel(t: string): string;
  getTraceStatusLabel(s: string): string;
  getNodeResultLabel(s: string): string;
  getRelativeTime(d: string): string;
  objectKeys(o: unknown): string[];
  formatExtractValue(v: unknown): string;
}

describe('EmailDetailComponent', () => {
  let acc: Access;
  let setEmail: (e: unknown) => void;
  let download: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    download = vi.fn();
    TestBed.configureTestingModule({
      imports: [EmailDetailComponent],
      providers: [
        { provide: I18nService, useValue: { t: (k: string) => k, lang: () => 'en' } },
        { provide: DomSanitizer, useValue: { bypassSecurityTrustHtml: (s: string) => s } },
        { provide: WorkspaceService, useValue: { activeAccount: signal(null) } },
        { provide: EmailService, useValue: { downloadAttachment: download } },
        { provide: ExportImportService, useValue: { downloadBlob: vi.fn() } },
      ],
    });
    const fixture = TestBed.createComponent(EmailDetailComponent);
    setEmail = (e) => fixture.componentRef.setInput('email', e);
    acc = fixture.componentInstance as unknown as Access;
  });

  it('linkedText escapes HTML and linkifies URLs', () => {
    setEmail({ id: '1', bodyText: 'see http://x.com now <b>' });
    const html = acc.linkedText();
    expect(html).toContain('<a href="http://x.com"');
    expect(html).toContain('&lt;b&gt;');
  });

  it('linkedText is empty when there is no body text', () => {
    setEmail({ id: '1', bodyText: null });
    expect(acc.linkedText()).toBe('');
  });

  it('parsedAttachments parses the JSON, or yields [] on malformed data', () => {
    setEmail({ id: '1', attachments: '[{"fileName":"a.pdf"}]' });
    expect(acc.parsedAttachments()).toEqual([{ fileName: 'a.pdf' }]);
    setEmail({ id: '2', attachments: '{bad' });
    expect(acc.parsedAttachments()).toEqual([]);
  });

  it('sanitizedHtml strips disallowed tags (script)', () => {
    setEmail({ id: '1', bodyHtml: '<p>hi</p><script>evil()</script>' });
    const html = acc.sanitizedHtml();
    expect(html).toContain('<p>hi</p>');
    expect(html).not.toContain('script');
  });

  it('toggleTraceOpen toggles the expanded trace id', () => {
    setEmail({ id: '1' });
    expect(acc.traceOpen()).toBeNull();
    acc.toggleTraceOpen('t1');
    expect(acc.traceOpen()).toBe('t1');
    acc.toggleTraceOpen('t1');
    expect(acc.traceOpen()).toBeNull();
  });

  it('label helpers map enum-ish values to i18n keys / fallbacks', () => {
    setEmail({ id: '1' });
    expect(acc.getNodeTypeLabel('FILTER')).toBe('inbox_node_filter');
    expect(acc.getTraceStatusLabel('SUCCESS')).toBe('inbox_trace_success');
    expect(acc.getTraceStatusLabel('WAT')).toBe('WAT');
    expect(acc.getNodeResultLabel('MATCHED')).toBe('inbox_filter_matched');
    expect(acc.getNodeResultLabel('SKIPPED')).toBe('Skipped');
    expect(acc.getNodeResultLabel('XYZ')).toBe('XYZ');
  });

  it('objectKeys returns keys for objects and [] otherwise', () => {
    setEmail({ id: '1' });
    expect(acc.objectKeys({ a: 1, b: 2 })).toEqual(['a', 'b']);
    expect(acc.objectKeys(null)).toEqual([]);
    expect(acc.objectKeys('str')).toEqual([]);
  });

  it('formatExtractValue stringifies values, dashing nullish', () => {
    setEmail({ id: '1' });
    expect(acc.formatExtractValue(null)).toBe('-');
    expect(acc.formatExtractValue({ a: 1 })).toBe('{"a":1}');
    expect(acc.formatExtractValue(42)).toBe('42');
    expect(acc.formatExtractValue('hi')).toBe('hi');
  });

  it('getRelativeTime returns a non-empty string', () => {
    setEmail({ id: '1' });
    expect(typeof acc.getRelativeTime(new Date().toISOString())).toBe('string');
  });
});
