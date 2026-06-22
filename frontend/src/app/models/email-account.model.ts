/** Server response representing a configured email account with IMAP/SMTP settings. */
export interface EmailAccount {
  id: string;
  email: string;
  displayName: string;
  color: string;
  readEnabled: boolean;
  writeEnabled: boolean;
  imapHost: string | null;
  imapPort: number | null;
  smtpHost: string | null;
  smtpPort: number | null;
  syncFromDate: string | null;
  isDefault: boolean;
  isActive: boolean;
  createdAt: string;
}

/** Request payload for creating or updating an email account including credentials. */
export interface EmailAccountRequest {
  email: string;
  displayName: string;
  color: string;
  readEnabled: boolean;
  writeEnabled: boolean;
  imapHost?: string;
  imapPort?: number;
  imapUsername?: string;
  imapPassword?: string;
  imapSsl?: boolean;
  smtpHost?: string;
  smtpPort?: number;
  smtpUsername?: string;
  smtpPassword?: string;
  smtpSsl?: boolean;
  syncFromDate?: string;
  isDefault: boolean;
}
