/** Supported data types for extraction parameters. */
export type ParameterType = 'TEXT' | 'NUMBER' | 'DATE' | 'EMAIL' | 'BOOLEAN' | 'OBJECT';
/** Subset of parameter types that represent scalar (non-nested) values. */
export type ScalarParameterType = Exclude<ParameterType, 'OBJECT'>;

/** Definition of a single parameter within a parameter set, supporting nested children. */
export interface ParameterItem {
  name: string;
  type: ParameterType;
  description: string;
  positiveExample: string;
  negativeExample: string;
  isList: boolean;
  required: boolean;
  children: ParameterItem[];
}

/** Server-side parameter set entity with ID, parameters, and timestamps. */
export interface ParameterSet {
  id: string;
  name: string;
  parameters: ParameterItem[];
  locked: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Request payload for creating or updating a parameter set. */
export interface ParameterSetRequest {
  name: string;
  parameters: ParameterItem[];
}

/** Portable parameter set representation used for JSON export and import. */
export interface ParameterSetExport {
  name: string;
  parameters: ParameterItem[];
}

export const RESERVED_PARAM_NAMES: ReadonlySet<string> = new Set([
  'fromAddress', 'fromName', 'subject', 'toAddress', 'receivedAt',
]);
