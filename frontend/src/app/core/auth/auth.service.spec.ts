import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthResponse } from './auth.models';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

const response = (access: string, refresh: string): AuthResponse => ({
  accessToken: access,
  refreshToken: refresh,
  expiresIn: 900,
  user: { id: 'u1', email: 'ada@example.com', fullName: 'Ada' },
});

describe('AuthService + authInterceptor', () => {
  let auth: AuthService;
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('login stores the user and refresh token', () => {
    auth.login({ email: 'ada@example.com', password: 'pw' }).subscribe();
    httpMock.expectOne('/api/auth/login').flush(response('a1', 'r1'));

    expect(auth.isAuthenticated()).toBeTrue();
    expect(auth.user()?.email).toBe('ada@example.com');
    expect(auth.hasRefreshToken()).toBeTrue();
  });

  it('adds the Bearer header to API calls but not to login', () => {
    auth.login({ email: 'ada@example.com', password: 'pw' }).subscribe();
    const login = httpMock.expectOne('/api/auth/login');
    expect(login.request.headers.has('Authorization')).toBeFalse();
    login.flush(response('a1', 'r1'));

    http.get('/api/auth/me').subscribe();
    expect(httpMock.expectOne('/api/auth/me').request.headers.get('Authorization')).toBe('Bearer a1');
  });

  it('refreshes once on 401 and retries with the new token', () => {
    auth.login({ email: 'ada@example.com', password: 'pw' }).subscribe();
    httpMock.expectOne('/api/auth/login').flush(response('a1', 'r1'));

    let result: unknown;
    http.get('/api/auth/me').subscribe((r) => (result = r));
    httpMock.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    const refresh = httpMock.expectOne('/api/auth/refresh');
    expect(refresh.request.body).toEqual({ refreshToken: 'r1' });
    refresh.flush(response('a2', 'r2'));

    const retry = httpMock.expectOne('/api/auth/me');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer a2');
    retry.flush({ ok: true });
    expect(result).toEqual({ ok: true });
  });

  it('logs out when the refresh fails', () => {
    auth.login({ email: 'ada@example.com', password: 'pw' }).subscribe();
    httpMock.expectOne('/api/auth/login').flush(response('a1', 'r1'));

    http.get('/api/auth/me').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBeFalse();
    expect(auth.hasRefreshToken()).toBeFalse();
  });
});
