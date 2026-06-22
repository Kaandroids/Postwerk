export interface Secret {
  id: string;
  name: string;
  description: string | null;
  version: number;
  lastRotatedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SecretRequest {
  name: string;
  description: string | null;
  value: string;
}
