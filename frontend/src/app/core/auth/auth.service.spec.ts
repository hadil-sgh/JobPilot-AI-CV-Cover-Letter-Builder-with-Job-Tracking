import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { TokenStorageService } from './token-storage.service';

describe('AuthService', () => {
  let service: AuthService;
  let tokenStorage: TokenStorageService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    tokenStorage = TestBed.inject(TokenStorageService);
    httpMock = TestBed.inject(HttpTestingController);
    localStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('stores tokens and publishes the current user on successful login', () => {
    let emitted: unknown;
    service.currentUser$.subscribe((user) => (emitted = user));

    service.login({ email: 'a@b.com', password: 'secret123' }).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      email: 'a@b.com',
      fullName: 'A B',
    });

    expect(tokenStorage.getAccessToken()).toBe('access-token');
    expect(tokenStorage.getRefreshToken()).toBe('refresh-token');
    expect(emitted).toEqual({ email: 'a@b.com', fullName: 'A B' });
  });

  it('reports isAuthenticated based on whether an access token is stored', () => {
    expect(service.isAuthenticated).toBe(false);

    service.register({ email: 'a@b.com', password: 'secret123' }).subscribe();
    httpMock
      .expectOne(`${environment.apiUrl}/auth/register`)
      .flush({ accessToken: 'x', refreshToken: 'y', email: 'a@b.com', fullName: null });

    expect(service.isAuthenticated).toBe(true);
  });

  it('clears tokens and current user on logout', () => {
    tokenStorage.setTokens('access-token', 'refresh-token');

    service.logout();

    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(tokenStorage.getRefreshToken()).toBeNull();
    expect(service.isAuthenticated).toBe(false);
  });
});
