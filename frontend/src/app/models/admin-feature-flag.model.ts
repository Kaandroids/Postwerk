import { Page } from './page.model';

export type { Page };

export type FeatureFlagKind = 'RELEASE' | 'OPS' | 'EXPERIMENT' | 'PERMISSION';
export type FlagAudience = 'EVERYONE' | 'PLAN' | 'ORG' | 'STAFF';
export type FlagStatus = 'ON' | 'ROLLING' | 'OFF' | 'KILLED' | 'ARCHIVED';

export interface FlagOverride {
  scope: string;
  value: 'on' | 'off';
}

export interface FeatureFlag {
  id: string;
  key: string;
  name: string;
  description: string | null;
  kind: FeatureFlagKind;
  enabled: boolean;
  rollout: number;
  audience: FlagAudience;
  audiencePlans: string[];
  audienceOrgId: string | null;
  audienceOrgName: string | null;
  overrides: FlagOverride[];
  killed: boolean;
  archived: boolean;
  stale: boolean;
  status: FlagStatus;
  updatedByName: string | null;
  updatedAt: string;
}

export interface FlagHistoryEntry {
  label: string;
  actor: string;
  at: string;
}

export interface FeatureFlagDetail {
  flag: FeatureFlag;
  history: FlagHistoryEntry[];
}

export interface FeatureFlagKpis {
  total: number;
  on: number;
  partial: number;
  off: number;
  killed: number;
  archived: number;
  stale: number;
  inFlight: number;
}

export interface FlagFilters {
  search?: string;
  kind?: '' | FeatureFlagKind;
  status?: '' | FlagStatus;
  targeting?: '' | FlagAudience;
  health?: '' | 'stale';
  sort?: '' | 'updated' | 'rollout' | 'status';
}

export interface CreateFlagRequest {
  key: string;
  name: string;
  description: string;
  kind: FeatureFlagKind;
}

export interface UpdateFlagRequest {
  name: string;
  description: string;
  kind: FeatureFlagKind;
  enabled: boolean;
  rollout: number;
  audience: FlagAudience;
  audiencePlans: string[];
  audienceOrgId: string | null;
  overrides: FlagOverride[];
}
