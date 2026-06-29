import { TestBed } from '@angular/core/testing';
import { GraphEdge, GraphNode, VariableGraphService } from './variable-graph.service';

function n(id: string, nodeType: GraphNode['nodeType'], config = '{}'): GraphNode {
  return { id, nodeType, label: id, config };
}

/** Flattens all exposed variable keys across the returned groups. */
function keysFor(service: VariableGraphService, nodeId: string, nodes: GraphNode[], edges: GraphEdge[]): string[] {
  return service.getAvailableVariables(nodeId, nodes, edges, [], []).flatMap(g => g.variables.map(v => v.key));
}

describe('VariableGraphService — attachments & FOREACH exposure', () => {
  let service: VariableGraphService;

  beforeEach(() => {
    service = TestBed.inject(VariableGraphService);
  });

  it('exposes email.attachments (and the rest of email.*) when an EMAIL trigger is upstream', () => {
    const nodes = [n('t1', 'TRIGGER', '{"triggerMode":"EMAIL"}'), n('x', 'SEND_EMAIL')];
    const edges: GraphEdge[] = [{ outputId: 't1', inputId: 'x' }];

    const keys = keysFor(service, 'x', nodes, edges);

    expect(keys).toContain('email.attachments');
    expect(keys).toContain('email.from');
    expect(keys).toContain('email.body');
  });

  it('does not expose email.* for a non-email (WEBHOOK) trigger', () => {
    const nodes = [n('t1', 'TRIGGER', '{"triggerMode":"WEBHOOK"}'), n('x', 'SEND_EMAIL')];
    const edges: GraphEdge[] = [{ outputId: 't1', inputId: 'x' }];

    const keys = keysFor(service, 'x', nodes, edges);

    expect(keys).not.toContain('email.attachments');
    expect(keys).not.toContain('email.from');
  });

  it('exposes the FOREACH item (current attachment + fields) downstream of an attachments loop', () => {
    const nodes = [
      n('t1', 'TRIGGER', '{"triggerMode":"EMAIL"}'),
      n('f1', 'FOREACH', '{"sourceVariable":"email.attachments","itemAlias":"item"}'),
      n('x', 'EXTRACT'),
    ];
    const edges: GraphEdge[] = [
      { outputId: 't1', inputId: 'f1' },
      { outputId: 'f1', inputId: 'x' },
    ];

    const keys = keysFor(service, 'x', nodes, edges);

    // the element itself (feeds the current attachment to AI) + its metadata + index/count
    expect(keys).toContain('item');
    expect(keys).toContain('item.name');
    expect(keys).toContain('item.contentType');
    expect(keys).toContain('item.size');
    expect(keys).toContain('item.index');
    expect(keys).toContain('item.count');
  });

  it('honours a custom item alias', () => {
    const nodes = [
      n('t1', 'TRIGGER', '{"triggerMode":"EMAIL"}'),
      n('f1', 'FOREACH', '{"sourceVariable":"email.attachments","itemAlias":"att"}'),
      n('x', 'EXTRACT'),
    ];
    const edges: GraphEdge[] = [
      { outputId: 't1', inputId: 'f1' },
      { outputId: 'f1', inputId: 'x' },
    ];

    const keys = keysFor(service, 'x', nodes, edges);

    expect(keys).toContain('att');
    expect(keys).toContain('att.name');
    expect(keys).not.toContain('item.name');
  });

  it('a non-attachment FOREACH exposes only index/count (no attachment fields, no bare alias)', () => {
    const nodes = [
      n('t1', 'TRIGGER', '{"triggerMode":"EMAIL"}'),
      n('f1', 'FOREACH', '{"sourceVariable":"trigger.items","itemAlias":"row"}'),
      n('x', 'EXTRACT'),
    ];
    const edges: GraphEdge[] = [
      { outputId: 't1', inputId: 'f1' },
      { outputId: 'f1', inputId: 'x' },
    ];

    const keys = keysFor(service, 'x', nodes, edges);

    expect(keys).toContain('row.index');
    expect(keys).toContain('row.count');
    expect(keys).not.toContain('row');
    expect(keys).not.toContain('row.name');
  });

  it('a node with no upstream gets no email or item variables', () => {
    const nodes = [n('t1', 'TRIGGER', '{"triggerMode":"EMAIL"}'), n('x', 'EXTRACT')];
    // no edges → x has no upstream
    const keys = keysFor(service, 'x', nodes, []);

    expect(keys).not.toContain('email.attachments');
    expect(keys).not.toContain('item');
  });
});
