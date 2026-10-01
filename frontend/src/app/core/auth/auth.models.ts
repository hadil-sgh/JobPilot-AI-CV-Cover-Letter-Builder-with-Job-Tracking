export interface User {
  id: string;
  email: string;
  fullName: string | null;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest extends LoginRequest {
  fullName?: string;
}

/** Error body returned by the backend (common/error/ErrorResponse). */
export interface ApiError {
  status: number;
  message: string;
  fieldErrors?: Record<string, string>;
}
