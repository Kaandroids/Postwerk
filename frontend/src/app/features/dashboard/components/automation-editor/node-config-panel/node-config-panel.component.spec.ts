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
 * Focuses on the EXTRACT/CATEGORIZE "Send attachments to AI" opt-in. The component is created without
 * change detection (no template render / no effects), so only the config read/write logic is exercised
 * against the `nodes` signal; all injected services are stubbed.
 */
describe('NodeConfigPanelComponent — include-attachments toggle', () => {
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
      getIncludeAttachments: (n: TestNode) => boolean;
      toggleIncludeAttachments: (id: string) => void;
    };
    const nodes: WritableSignal<TestNode[]> = signal([
      { id: 'n1', nodeType: 'EXTRACT', label: 'Extract', position: { x: 0, y: 0 }, config: initialConfig },
    ]);
    comp.nodes = nodes;
    return { comp, nodes };
  }

  it('defaults to false when the flag is absent', () => {
    const { comp, nodes } = setup();
    expect(comp.getIncludeAttachments(nodes()[0])).toBe(false);
  });

  it('toggles includeAttachments on then off, persisting it in the node config', () => {
    const { comp, nodes } = setup();

    comp.toggleIncludeAttachments('n1');
    expect(comp.getIncludeAttachments(nodes()[0])).toBe(true);
    expect(JSON.parse(nodes()[0].config).includeAttachments).toBe(true);

    comp.toggleIncludeAttachments('n1');
    expect(comp.getIncludeAttachments(nodes()[0])).toBe(false);
  });

  it('reads an existing true flag from the config', () => {
    const { comp, nodes } = setup('{"includeAttachments":true}');
    expect(comp.getIncludeAttachments(nodes()[0])).toBe(true);
  });
});
