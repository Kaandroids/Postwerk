/** Result summary of a bulk import operation including success/failure counts and error details. */
export interface ImportResult {
  imported: number;
  failed: number;
  errors: string[];
}
