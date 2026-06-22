const author = {
  id: 'author-1',
  name: 'Ada Lovelace',
  verified: true,
  listingCount: 3,
  installCount: 420,
} as const;

/** A PUBLIC, free listing (fully editable copy on install). */
export const mockPublicListing = {
  id: 'list-pub-1',
  name: 'Lead Router',
  tagline: 'Route inbound leads to the right inbox',
  description: '<p>Parses inbound sales mail and <strong>forwards</strong> to the right rep.</p>',
  category: 'sales',
  visibility: 'PUBLIC' as const,
  pricingModel: 'FREE' as const,
  price: null,
  version: '1.2.0',
  icon: 'workflow',
  color: '#6366f1',
  ioInIcon: 'inbox',
  ioInLabel: 'Inbound email',
  ioOutIcon: 'send',
  ioOutLabel: 'Forwarded email',
  nodeCount: 4,
  constantCount: 1,
  ratingAvg: 4.5,
  ratingCount: 12,
  installCount: 230,
  featured: true,
  verified: true,
  status: 'PUBLISHED' as const,
  author,
  owned: false,
  createdAt: '2024-05-01T10:00:00Z',
  updatedAt: '2024-05-10T10:00:00Z',
};

/** A PRIVATE, one-time-priced listing (content-hidden copy on install). */
export const mockPrivateListing = {
  id: 'list-prv-1',
  name: 'Invoice Sentinel',
  tagline: 'Detect and file invoices automatically',
  description: 'Watches for invoice mail and files it.',
  category: 'finance',
  visibility: 'PRIVATE' as const,
  pricingModel: 'ONE_TIME' as const,
  price: 19,
  version: '2.0.0',
  icon: 'shield',
  color: '#10b981',
  ioInIcon: 'inbox',
  ioInLabel: 'Inbound email',
  ioOutIcon: 'tag',
  ioOutLabel: 'Filed invoice',
  nodeCount: 6,
  constantCount: 2,
  ratingAvg: 0,
  ratingCount: 0,
  installCount: 58,
  featured: false,
  verified: false,
  status: 'PUBLISHED' as const,
  author,
  owned: false,
  createdAt: '2024-06-01T10:00:00Z',
  updatedAt: '2024-06-02T10:00:00Z',
};

export const mockDiscoverListings = [mockPublicListing, mockPrivateListing];

export const mockReviews = [
  {
    id: 'rev-1',
    userId: 'user-9',
    userName: 'Grace Hopper',
    rating: 5,
    text: 'Saved me hours every week.',
    createdAt: '2024-05-20T12:00:00Z',
    mine: false,
  },
];

/** Reviews where the first entry belongs to the requesting user (shows the "Deine" badge). */
export const mockOwnReviews = [
  {
    id: 'rev-mine',
    userId: 'user-self',
    userName: 'You',
    rating: 4,
    text: 'My own take on it.',
    createdAt: '2024-06-01T12:00:00Z',
    mine: true,
  },
  mockReviews[0],
];

/** Detail of the PUBLIC listing — exposes the node-flow preview. */
export const mockPublicDetail = {
  listing: mockPublicListing,
  nodeFlow: [
    { nodeType: 'TRIGGER', label: 'New email' },
    { nodeType: 'CATEGORIZE', label: 'Classify' },
    { nodeType: 'EMAIL_ACTION', label: 'Forward' },
  ],
  publishableConstants: [],
  reviews: mockReviews,
};

/** Detail of the PRIVATE listing — exposes only publishable-constant metadata. */
export const mockPrivateDetail = {
  listing: mockPrivateListing,
  nodeFlow: [],
  publishableConstants: [
    { name: 'API_KEY', description: 'Accounting API key', type: 'secret' as const },
    { name: 'THRESHOLD', description: 'Minimum amount', type: 'number' as const },
  ],
  reviews: [],
};

/** Acquisition produced by installing the PRIVATE listing (hidden → goes to Configure). */
export const mockPrivateAcquisition = {
  id: 'acq-prv-1',
  listingId: mockPrivateListing.id,
  installedAutomationId: 'auto-100',
  pricingModel: mockPrivateListing.pricingModel,
  price: mockPrivateListing.price,
  status: 'ACTIVE' as const,
  hidden: true,
  installedStatus: 'PAUSED',
  listing: mockPrivateListing,
  createdAt: '2024-06-10T10:00:00Z',
};

/** Acquisition produced by installing the PUBLIC listing (editable → goes to editor). */
export const mockPublicAcquisition = {
  id: 'acq-pub-1',
  listingId: mockPublicListing.id,
  installedAutomationId: 'auto-200',
  pricingModel: mockPublicListing.pricingModel,
  price: mockPublicListing.price,
  status: 'ACTIVE' as const,
  hidden: false,
  installedStatus: 'PAUSED',
  listing: { ...mockPublicListing, owned: true },
  createdAt: '2024-06-11T10:00:00Z',
};

/** The buyer's library: one editable install, one paid (purchased), one published listing. */
export const mockLibrary = {
  installed: [mockPublicAcquisition],
  purchased: [mockPrivateAcquisition],
  published: [{ ...mockPublicListing, id: 'list-mine-1', name: 'My Published Flow' }],
};
