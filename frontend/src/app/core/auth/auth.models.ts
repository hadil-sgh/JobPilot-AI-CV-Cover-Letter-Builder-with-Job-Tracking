export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  email: string;
  fullName: string | null;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}
