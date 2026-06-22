export const mockWizardSessionResponse = {
  sessionId: 'wiz-session-001',
  phase: 'chatting',
  messages: [],
  automationPlan: null,
};

export const mockWizardSessionReady = {
  sessionId: 'wiz-session-001',
  phase: 'ready',
  messages: [
    { role: 'user', content: 'Automate my support emails', timestamp: '2024-01-01T00:00:00Z' },
    { role: 'assistant', content: 'I have built your automation.', timestamp: '2024-01-01T00:01:00Z' },
  ],
  automationPlan: {
    automationId: 'auto-001',
    nodes: [
      {
        id: 'node_0', nodeType: 'TRIGGER', label: 'Email Trigger', positionX: 24, positionY: 176,
        config: JSON.stringify({ folder: 'Posteingang · k.kara@duahotel.de' }),
      },
      {
        id: 'node_1', nodeType: 'FILTER', label: 'VIP Filter', positionX: 296, positionY: 176,
        config: JSON.stringify({
          condition: 'absender ∈ VIP-Liste',
          outs: [{ tag: 'Match', type: 'hit' }, { tag: 'No match', type: 'miss' }],
        }),
      },
      {
        id: 'node_2', nodeType: 'CATEGORIZE', label: 'Classify', positionX: 568, positionY: 160,
        config: JSON.stringify({
          cats: [
            { id: 'complaint', name: 'Complaint', color: '#d6494c', conf: '12%' },
            { id: 'booking', name: 'Booking', color: '#6b8eda', conf: '94%' },
          ],
        }),
      },
      {
        id: 'node_3', nodeType: 'EXTRACT', label: 'Extract guest data', positionX: 840, positionY: 168,
        config: JSON.stringify({
          fields: [
            { key: 'Name', value: 'L. Sommer' },
            { key: 'Booking', value: '#DH-20815' },
          ],
        }),
      },
      {
        id: 'node_4', nodeType: 'EMAIL_ACTION', label: 'Auto-reply', positionX: 1048, positionY: 160,
        config: JSON.stringify({ actionMode: 'REPLY', mode: 'REPLY', reply: 'Thank you for your email.' }),
      },
    ],
    edges: [
      { id: 'edge_0', sourceNodeId: 'node_0', sourceHandle: 'output', targetNodeId: 'node_1', targetHandle: 'input' },
      { id: 'edge_1', sourceNodeId: 'node_1', sourceHandle: 'output', targetNodeId: 'node_2', targetHandle: 'input' },
      { id: 'edge_2', sourceNodeId: 'node_2', sourceHandle: 'output', targetNodeId: 'node_3', targetHandle: 'input' },
      { id: 'edge_3', sourceNodeId: 'node_3', sourceHandle: 'output', targetNodeId: 'node_4', targetHandle: 'input' },
    ],
  },
};

export const mockWizardClaimResponse = {
  automationId: 'real-auto-001',
};

/**
 * Builds a mock SSE response string for the wizard chat.
 * Returns a simple reply event sequence.
 */
export function buildWizardChatSSE(reply: string, sessionId = 'wiz-session-001'): string {
  const events = [
    `data:${JSON.stringify({ type: 'reply', content: reply, conversationId: sessionId })}\n\n`,
    `data:${JSON.stringify({ type: 'done', conversationId: sessionId, phase: 'chatting' })}\n\n`,
  ];
  return events.join('');
}

/**
 * Builds a mock SSE response that includes tool calls and transitions to ready.
 */
export function buildWizardBuildSSE(sessionId = 'wiz-session-001'): string {
  const nodes = [
    {
      id: 'node_0', nodeType: 'TRIGGER', label: 'Email Trigger', positionX: 24, positionY: 176,
      config: JSON.stringify({ folder: 'Inbox · test@example.com' }),
    },
    {
      id: 'node_1', nodeType: 'FILTER', label: 'Support Filter', positionX: 296, positionY: 176,
      config: JSON.stringify({
        condition: 'subject contains support',
        outs: [{ tag: 'Match', type: 'hit' }, { tag: 'No match', type: 'miss' }],
      }),
    },
  ];
  const edges = [
    { id: 'edge_0', sourceNodeId: 'node_0', sourceHandle: 'output', targetNodeId: 'node_1', targetHandle: 'input' },
  ];

  const events = [
    `data:${JSON.stringify({ type: 'phase', phase: 'building', conversationId: sessionId })}\n\n`,
    `data:${JSON.stringify({ type: 'tool_start', tool: 'create_automation', args: { name: 'Support Auto' }, conversationId: sessionId })}\n\n`,
    `data:${JSON.stringify({ type: 'tool_result', tool: 'create_automation', result: { id: 'auto-001', name: 'Support Auto' }, success: true, conversationId: sessionId })}\n\n`,
    `data:${JSON.stringify({ type: 'tool_start', tool: 'update_automation_flow', args: {}, conversationId: sessionId })}\n\n`,
    `data:${JSON.stringify({
      type: 'tool_result',
      tool: 'update_automation_flow',
      result: { automationId: 'auto-001', nodes, edges },
      success: true,
      conversationId: sessionId,
    })}\n\n`,
    `data:${JSON.stringify({ type: 'reply', content: 'Your automation is ready!', conversationId: sessionId })}\n\n`,
    `data:${JSON.stringify({ type: 'phase', phase: 'ready', conversationId: sessionId })}\n\n`,
    `data:${JSON.stringify({ type: 'done', conversationId: sessionId, phase: 'ready' })}\n\n`,
  ];
  return events.join('');
}
