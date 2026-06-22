/** Mock data for the Knowledge Base feature e2e tests. Shapes mirror the backend DTOs. */

export const mockKbParameterSets = [
  {
    id: 'ps-1',
    name: 'SKR Schema',
    parameters: [
      { name: 'kod', type: 'TEXT', description: '', positiveExample: '', negativeExample: '', isList: false, required: false, children: [] },
      { name: 'isim', type: 'TEXT', description: '', positiveExample: '', negativeExample: '', isList: false, required: false, children: [] },
    ],
    locked: false,
    createdAt: '2024-01-20T10:00:00Z',
    updatedAt: '2024-01-20T10:00:00Z',
  },
];

export const mockKnowledgeBases = [
  {
    id: 'kb-1',
    name: 'SKR 03',
    description: 'Kontenrahmen',
    parameterSetId: 'ps-1',
    fieldRoles: { isim: { embed: true, keyword: true }, kod: { embed: false, keyword: true } },
    uniqueField: 'kod',
    entryCount: 2,
    locked: false,
    createdAt: '2024-02-01T10:00:00Z',
    updatedAt: '2024-02-01T10:00:00Z',
  },
];

export const mockKbCreated = {
  id: 'kb-2',
  name: 'Produkte',
  description: '',
  parameterSetId: 'ps-1',
  fieldRoles: { isim: { embed: true, keyword: true } },
  uniqueField: null,
  entryCount: 0,
  locked: false,
  createdAt: '2024-02-02T10:00:00Z',
  updatedAt: '2024-02-02T10:00:00Z',
};

export const mockKbEntries = [
  { id: 'e-1', data: { kod: '4930', isim: 'Bürobedarf' }, createdAt: '2024-02-01T10:00:00Z', updatedAt: '2024-02-01T10:00:00Z' },
  { id: 'e-2', data: { kod: '8400', isim: 'Erlöse' }, createdAt: '2024-02-01T10:00:00Z', updatedAt: '2024-02-01T10:00:00Z' },
];

export const mockKbEntryCreated = {
  id: 'e-3',
  data: { kod: '4980', isim: 'Sonstige Kosten' },
  createdAt: '2024-02-02T10:00:00Z',
  updatedAt: '2024-02-02T10:00:00Z',
};

export const mockKbImportResult = { imported: 3, failed: 0, errors: [] as string[] };
