/** Server-side category entity with ID, color, and classification examples. */
export interface Category {
  id: string;
  name: string;
  color: string;
  description: string;
  positiveExample: string | null;
  negativeExample: string | null;
  locked: boolean;
  createdAt: string;
}

/** Request payload for creating or updating a category. */
export interface CategoryRequest {
  name: string;
  color: string;
  description: string;
  positiveExample?: string;
  negativeExample?: string;
}

/** Portable category representation used for JSON export and import. */
export interface CategoryExport {
  name: string;
  color: string;
  description: string;
  positiveExample: string | null;
  negativeExample: string | null;
}
