import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, RegisterRequest } from './auth.models';
import { TokenStorageService } from './token-storage.service';

export interface CurrentUser {
  email: string;
  fullName: string | null;
}

// Angular 15 has no signals API (added in v16/17, see docs/DECISIONS.md), so auth state
// is exposed as a BehaviorSubject instead.
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly currentUserSubject = new BehaviorSubject<CurrentUser | null>(null);
  readonly currentUser$ = this.currentUserSubject.asObservable();

  constructor(
    private readonly http: HttpClient,
    private readonly tokenStorage: TokenStorageService,
  ) {}

  get isAuthenticated(): boolean {
    return !!this.tokenStorage.getAccessToken();
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/auth/register`, request)
      .pipe(tap((response) => this.handleAuthResponse(response)));
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/auth/login`, request)
      .pipe(tap((response) => this.handleAuthResponse(response)));
  }

  refresh(): Observable<AuthResponse> {
    const refreshToken = this.tokenStorage.getRefreshToken();
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/auth/refresh`, { refreshToken })
      .pipe(tap((response) => this.handleAuthResponse(response)));
  }

  logout(): void {
    this.tokenStorage.clear();
    this.currentUserSubject.next(null);
  }

  private handleAuthResponse(response: AuthResponse): void {
    this.tokenStorage.setTokens(response.accessToken, response.refreshToken);
    this.currentUserSubject.next({ email: response.email, fullName: response.fullName });
  }
}
