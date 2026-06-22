import { TestBed } from '@angular/core/testing';
import { AutomationLintService } from './automation-lint.service';
import { GraphEdge, GraphNode } from './variable-graph.service';
import { NodeType } from '../../../../models/automation.model';

function n(id: string, nodeType: NodeType, config = '{}'): GraphNode {
  return { id, nodeType, label: id, config };
}

describe('AutomationLintService', () => {
  let service: AutomationLintService;

  beforeEach(() => {
    service = TestBed.inject(AutomationLintService);
  });

  it('flags MISSING_TRIGGER for an automation with no trigger node', () => {
    const issues = service.lint('AUTOMATION', [n('s1', 'SEND_EMAIL', '{"to":"a@b.com"}')], [], [], []);
    expect(issues.some(i => i.code === 'MISSING_TRIGGER' && i.severity === 'error')).toBe(true);
  });

  it('does not flag MISSING_TRIGGER for an integration (uses INPUT, not TRIGGER)', () => {
    const issues = service.lint('INTEGRATION', [n('i1', 'INPUT')], [], [], []);
    expect(issues.some(i => i.code === 'MISSING_TRIGGER')).toBe(false);
  });

  it('flags ORPHAN_NODE (warning) for a disconnected non-trigger node', () => {
    const nodes = [n('t1', 'TRIGGER'), n('s1', 'SEND_EMAIL', '{"to":"a@b.com"}')];
    const issues = service.lint('AUTOMATION', nodes, [], [], []);
    expect(issues.some(i => i.code === 'ORPHAN_NODE' && i.nodeId === 's1' && i.severity === 'warning')).toBe(true);
  });

  it('does not flag ORPHAN_NODE when the node has an incoming edge', () => {
    const nodes = [n('t1', 'TRIGGER'), n('s1', 'SEND_EMAIL', '{"to":"a@b.com"}')];
    const edges: GraphEdge[] = [{ outputId: 't1', inputId: 's1' }];
    const issues = service.lint('AUTOMATION', nodes, edges, [], []);
    expect(issues.some(i => i.code === 'ORPHAN_NODE')).toBe(false);
  });

  it('flags SEND_NO_RECIPIENT when a send node has no recipient', () => {
    const nodes = [n('t1', 'TRIGGER'), n('s1', 'SEND_EMAIL', '{}')];
    const edges: GraphEdge[] = [{ outputId: 't1', inputId: 's1' }];
    const issues = service.lint('AUTOMATION', nodes, edges, [], []);
    expect(issues.some(i => i.code === 'SEND_NO_RECIPIENT' && i.nodeId === 's1')).toBe(true);
  });

  it('returns no error-severity issues for a minimal valid flow', () => {
    const nodes = [n('t1', 'TRIGGER'), n('s1', 'SEND_EMAIL', '{"to":"a@b.com"}')];
    const edges: GraphEdge[] = [{ outputId: 't1', inputId: 's1' }];
    const issues = service.lint('AUTOMATION', nodes, edges, [], []);
    expect(issues.filter(i => i.severity === 'error')).toEqual([]);
  });
});
