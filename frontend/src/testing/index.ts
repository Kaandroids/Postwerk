/**
 * Shared unit-test helpers. Import from {@code src/testing} (e.g. {@code import { createMockApi }
 * from '../../../../testing'}) to keep specs DRY: a mock {@code ApiService}, an HttpTestingController
 * harness for HttpClient-direct services, and an echo {@code I18nService} stub.
 */
export * from './mock-api';
export * from './http';
export * from './stubs';
