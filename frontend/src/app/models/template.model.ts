/** Server-side email template entity with parsed parameters and linked parameter set. */
export interface Template {
  id: string;
  name: string;
  subject: string;
  body: string;
  params: string[];
  parameterSetId: string | null;
  parameterSetName: string | null;
  locked: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Request payload for creating or updating an email template. */
export interface TemplateRequest {
  name: string;
  subject: string;
  body: string;
  parameterSetId?: string | null;
}

/** Portable template representation used for JSON export and import. */
export interface TemplateExport {
  name: string;
  subject: string;
  body: string;
}
