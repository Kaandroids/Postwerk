import { signal, WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NodeConfigPanelComponent } from './node-config-panel.component';
import { I18nService } from '../../../../../core/services/i18n.service';
import { WorkspaceService } from '../../../../../core/services/workspace.service';
import { WebhookEndpointService } from '../../../../../core/services/webhook-endpoint.service';
import { AutomationService } from '../../../../../core/services/automation.service';
import { KnowledgeBaseService } from '../../../../../core/services/knowledge-base.service';
import { OrganizationService } from '../../../../../core/services/organization.service';
import { ViewportService } from '../../../../../core/services/viewport.service';
import { VariableGraphService } from '../variable-graph.service';

interface TestNode { id: string; nodeType: string; label: string; position: { x: number; y: number }; config: string; }

/**
 * Focuses on the FORWARD attachment source (get/setForwardAttachmentSource): none / all original / current loop item.
 * The component is created without change detection (no template render / no effects), so only the config
 * read/write logic is exercised against the `nodes` signal; all injected services are stubbed.
 */
describe('NodeConfigPanelComponent — FORWARD attachment source', () => {
  function setup(initialConfig = '{}') {
    TestBed.configureTestingModule({
      imports: [NodeConfigPanelComponent],
      providers: [
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: WorkspaceService, useValue: {} },
        { provide: WebhookEndpointService, useValue: {} },
        { provide: AutomationService, useValue: {} },
        { provide: KnowledgeBaseService, useValue: {} },
        { provide: OrganizationService, useValue: { can: () => true } },
        { provide: ViewportService, useValue: { isMobile: () => false } },
        { provide: VariableGraphService, useValue: {} },
      ],
    });
    const comp = TestBed.createComponent(NodeConfigPanelComponent).componentInstance as unknown as {
      nodes: WritableSignal<TestNode[]>;
      getForwardAttachmentSource: (n: TestNode) => string;
      setForwardAttachmentSource: (id: string, value: string) => void;
    };
    const nodes: WritableSignal<TestNode[]> = signal([
      { id: 'n1', nodeType: 'EMAIL_ACTION', label: 'Forward', position: { x: 0, y: 0 }, config: initialConfig },
    ]);
    comp.nodes = nodes;
    return { comp, nodes };
  }

  it('defaults to empty when no attachment source is set', () => {
    const { comp, nodes } = setup();
    expect(comp.getForwardAttachmentSource(nodes()[0])).toBe('');
  });

  it('reads the legacy includeAttachments=true as all attachments', () => {
    const { comp, nodes } = setup('{"includeAttachments":true}');
    expect(comp.getForwardAttachmentSource(nodes()[0])).toBe('email.attachments');
  });

  it('sets the attachment source and clears the legacy flag', () => {
    const { comp, nodes } = setup('{"includeAttachments":true}');

    comp.setForwardAttachmentSource('n1', 'item');

    expect(comp.getForwardAttachmentSource(nodes()[0])).toBe('item');
    const cfg = JSON.parse(nodes()[0].config);
    expect(cfg.attachmentSource).toBe('item');
    expect('includeAttachments' in cfg).toBe(false);
  });
});
