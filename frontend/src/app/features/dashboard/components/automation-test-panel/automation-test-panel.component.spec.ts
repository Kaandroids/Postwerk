import { TestBed } from '@angular/core/testing';
import { DomSanitizer } from '@angular/platform-browser';
import { AutomationTestPanelComponent } from './automation-test-panel.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AutomationTestService } from '../../../../core/services/automation-test.service';

interface TestAtt { name: string; contentType: string; size: number }

/**
 * Focuses on the FOREACH/attachment additions to the Test panel: the mock-attachment rows and the
 * FOREACH node-detail formatter. Created without change detection (no template render / no effects),
 * so only the signal/format logic runs; all injected services are stubbed.
 */
describe('AutomationTestPanelComponent — mock attachments & FOREACH details', () => {
  function setup() {
    TestBed.configureTestingModule({
      imports: [AutomationTestPanelComponent],
      providers: [
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: FormatService, useValue: {} },
        { provide: AutomationTestService, useValue: {} },
        { provide: DomSanitizer, useValue: { bypassSecurityTrustHtml: (s: string) => s } },
      ],
    });
    return TestBed.createComponent(AutomationTestPanelComponent).componentInstance as unknown as {
      formAttachments: () => TestAtt[];
      addAttachment: () => void;
      removeAttachment: (i: number) => void;
      updateAttachment: (i: number, f: 'name' | 'contentType' | 'size', v: string) => void;
      getStatusOptions: (id: string) => string[];
      formatNodeDetails: (n: { nodeType: string; resultDetail: Record<string, unknown> }) => { label: string; value: string }[];
    };
  }

  it('adds, updates (coercing size to a number), and removes mock attachments', () => {
    const c = setup();

    c.addAttachment();
    expect(c.formAttachments()).toHaveLength(1);

    c.updateAttachment(0, 'name', 'invoice.pdf');
    c.updateAttachment(0, 'contentType', 'application/pdf');
    c.updateAttachment(0, 'size', '2048');
    expect(c.formAttachments()[0]).toEqual({ name: 'invoice.pdf', contentType: 'application/pdf', size: 2048 });

    c.removeAttachment(0);
    expect(c.formAttachments()).toHaveLength(0);
  });

  it('offers PASSED/SKIPPED status options for an unknown/empty node (FOREACH default)', () => {
    const c = setup();
    // No nodes input set → falls back to the default, which matches FOREACH's status set.
    expect(c.getStatusOptions('whatever')).toEqual(['PASSED', 'SKIPPED']);
  });

  it('formats FOREACH node details (source, count, alias)', () => {
    const c = setup();

    const items = c.formatNodeDetails({
      nodeType: 'FOREACH',
      resultDetail: { source: 'email.attachments', count: 3, alias: 'item' },
    });
    const values = items.map(i => i.value);

    expect(values).toContain('email.attachments');
    expect(values).toContain('3');
    expect(values).toContain('item');
  });
});
