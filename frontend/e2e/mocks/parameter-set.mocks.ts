/** Mock data for the parameter-sets CRUD page (/dashboard/parameter-sets). */

export const mockParameterSets = [
  {
    id: 'ps-1',
    name: 'Invoice Fields',
    locked: false,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    parameters: [
      { name: 'amount', type: 'NUMBER', description: 'Total amount', positiveExample: '100.00', negativeExample: '', isList: false, required: true, children: [] },
      { name: 'dueDate', type: 'DATE', description: 'Payment due', positiveExample: '2026-02-01', negativeExample: '', isList: false, required: false, children: [] },
    ],
  },
  {
    id: 'ps-2',
    name: 'Contact Info',
    locked: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    parameters: [
      { name: 'fullName', type: 'TEXT', description: 'Sender name', positiveExample: 'Jane Doe', negativeExample: '', isList: false, required: true, children: [] },
    ],
  },
];

export const mockEmptyParameterSets: typeof mockParameterSets = [];
