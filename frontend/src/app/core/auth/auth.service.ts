import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, finalize, firstValueFrom, map, shareReplay, tap } from 'rxjs';

import { AuthResponse, LoginRequest, RegisterRequest, User } from './auth.models';

const REFRESH_KEY = 'jobpilot.refreshToken';

/**
 * Session state as signals. The access token lives only in memory; the refresh token is kept in
 * localStorage so a page reload can restore the session (see docs/DECISIONS.md).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _user = signal<User | null>(null);
  private accessToken: string | null = null;
  private refreshInFlight: Observable<string> | null = null;

  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);

  getAccessToken(): string | null {
    return this.accessToken;
  }

  login(req: LoginRequest): Observable<User> {
    return this.http.post<AuthResponse>('/api/auth/login', req).pipe(
      tap((res) => this.setSession(res)),
      map((res) => res.user),
    );
  }

  register(req: RegisterRequest): Observable<User> {
    return this.http.post<AuthResponse>('/api/auth/register', req).pipe(
      tap((res) => this.setSession(res)),
      map((res) => res.user),
    );
  }

  /** Exchanges the stored refresh token for a new pair. Concurrent callers share one request. */
  refresh(): Observable<string> {
    if (!this.refreshInFlight) {
      const refreshToken = readRefreshToken();
      this.refreshInFlight = this.http
        .post<AuthResponse>('/api/auth/refresh', { refreshToken: refreshToken ?? '' })
        .pipe(
          tap({ next: (res) => this.setSession(res), error: () => this.clearSession() }),
          map((res) => res.accessToken),
          finalize(() => (this.refreshInFlight = null)),
          shareReplay(1),
        );
    }
    return this.refreshInFlight;
  }

  hasRefreshToken(): boolean {
    return readRefreshToken() !== null;
  }

  /** Called once at startup: silently restores the session if a refresh token is stored. */
  async restoreSession(): Promise<void> {
    if (!this.hasRefreshToken()) {
      return;
    }
    try {
      await firstValueFrom(this.refresh());
    } catch {
      // Expired or revoked: stay logged out.
    }
  }

  logout(): void {
    const refreshToken = readRefreshToken();
    if (refreshToken) {
      this.http.post('/api/auth/logout', { refreshToken }).subscribe({ error: () => undefined });
    }
    this.clearSession();
    this.router.navigateByUrl('/login');
  }

  private setSession(res: AuthResponse): void {
    this.accessToken = res.accessToken;
    writeRefreshToken(res.refreshToken);
    this._user.set(res.user);
  }

  private clearSession(): void {
    this.accessToken = null;
    writeRefreshToken(null);
    this._user.set(null);
  }
}

function readRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_KEY);
  } catch {
    return null;
  }
}

function writeRefreshToken(token: string | null): void {
  try {
    if (token) {
      localStorage.setItem(REFRESH_KEY, token);
    } else {
      localStorage.removeItem(REFRESH_KEY);
    }
  } catch {
    // Storage unavailable (private mode): session just won't survive a reload.
  }
}
