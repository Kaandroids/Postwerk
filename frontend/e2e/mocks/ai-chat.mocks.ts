export const mockConversations = [
  {
    id: 'conv-1',
    title: 'Spam-Filter erstellen',
    updatedAt: '2024-03-15T14:30:00Z',
  },
  {
    id: 'conv-2',
    title: 'Kategorie anlegen',
    updatedAt: '2024-03-14T10:00:00Z',
  },
];

export const mockConversationDetail = {
  id: 'conv-1',
  title: 'Spam-Filter erstellen',
  phase: 'OPEN',
  messages: [
    {
      role: 'user',
      content: 'Erstelle einen Filter für Spam-Mails',
      timestamp: '2024-03-15T14:28:00Z',
    },
    {
      role: 'assistant',
      content: 'Ich habe einen Spam-Filter für Sie erstellt.',
      timestamp: '2024-03-15T14:28:05Z',
      toolCalls: [
        {
          tool: 'create_filter',
          args: { name: 'Spam', color: '#ef4444' },
          result: { id: 1, name: 'Spam', color: '#ef4444', description: 'Filtert Spam-Mails' },
          success: true,
        },
      ],
    },
  ],
};

export const mockConversationDetailPlanning = {
  id: 'conv-3',
  title: 'Automation planen',
  phase: 'PLANNING',
  messages: [
    {
      role: 'user',
      content: 'Erstelle eine Automation die Spam filtert',
      timestamp: '2024-03-15T14:28:00Z',
    },
    {
      role: 'system',
      content: 'PLANNING',
      timestamp: '2024-03-15T14:28:03Z',
      phase: 'PLANNING',
    },
    {
      role: 'assistant',
      content: 'Hier ist mein Plan für die Spam-Automation:\n\n1. **Trigger:** Neue E-Mail empfangen\n2. **Filter:** Absender enthält "noreply"\n3. **Aktion:** In Spam-Ordner verschieben',
      timestamp: '2024-03-15T14:28:05Z',
      toolCalls: [
        {
          tool: 'propose_automation_plan',
          args: { planSummary: 'Automation zum Filtern von Spam-Mails basierend auf Absender' },
          result: { success: true, planSummary: 'Automation zum Filtern von Spam-Mails basierend auf Absender' },
          success: true,
        },
      ],
    },
  ],
};

export const mockEmptyConversations: unknown[] = [];

export const mockChatStreamDone = {
  type: 'done',
  conversationId: 'conv-new',
  content: 'Das habe ich für Sie erledigt.',
  phase: 'OPEN',
};
