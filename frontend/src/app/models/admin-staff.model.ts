import { Page } from './page.model';

export type { Page };

export type StaffRoleKey = 'SUPER_ADMIN' | 'ADMIN' | 'BILLING' | 'MODERATOR' | 'SUPPORT' | 'AUDITOR';
export type StaffTier = 'PRIVILEGED' | 'READ_ONLY';

export interface StaffMember {
  id: string;
  name: string;
  email: string;
  role: StaffRoleKey;
  tier: StaffTier;
  capabilityCount: number;
  lastActiveAt: string | null;
  staffSince: string | null;
  self: boolean;
}

export interface StaffKpis {
  total: number;
  superAdmins: number;
  privileged: number;
  readOnly: number;
  added30d: number;
}

export interface StaffRoleInfo {
  key: StaffRoleKey;
  tier: StaffTier;
  privileged: boolean;
  permissions: string[];
}

export interface StaffRolesMatrix {
  roles: StaffRoleInfo[];
  allPermissions: string[];
}

export interface StaffCandidate {
  id: string;
  name: string;
  email: string;
}

export interface StaffFilters {
  search?: string;
  role?: '' | StaffRoleKey;
  tier?: '' | StaffTier;
  sort?: '' | 'role' | 'lastActive' | 'staffSince';
}
