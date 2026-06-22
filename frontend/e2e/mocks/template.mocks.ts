export const mockTemplates = [
  {
    id: 1,
    name: 'Bestellbestätigung',
    subject: 'Ihre Bestellung {{orderNumber}} wurde bestätigt',
    body: '<p>Sehr geehrte/r {{customerName}},</p><p>Ihre Bestellung <strong>{{orderNumber}}</strong> wurde erfolgreich aufgenommen.</p><p>Mit freundlichen Grüßen</p>',
    params: ['orderNumber', 'customerName'],
    parameterSetId: null,
    parameterSetName: null,
    locked: false,
    createdAt: '2024-02-10T10:00:00Z',
  },
];
