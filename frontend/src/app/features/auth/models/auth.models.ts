export interface LoginRequest {
  email: string;
  password: string;
  remember: boolean;
}

export interface RegisterRequest {
  fullName: string;
  email: string;
  company?: string;
  phone?: string;
  password: string;
  acceptTerms: boolean;
  acceptMarketing: boolean;
}

export interface ResetPasswordRequest {
  email: string;
}
