import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { TokenStorageService } from './token-storage.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let tokenStorage: TokenStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    tokenStorage = TestBed.inject(TokenStorageService);
    localStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('attaches a Bearer token to non-auth requests when one is stored', () => {
    tokenStorage.setTokens('access-token', 'refresh-token');

    http.get('/api/applications').subscribe();

    const req = httpMock.expectOne('/api/applications');
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-token');
    req.flush({});
  });

  it('does not attach a token to auth endpoints even when one is stored', () => {
    tokenStorage.setTokens('access-token', 'refresh-token');

    http.post('/api/auth/login', {}).subscribe();

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('logs out and redirects on a 401 from a non-auth endpoint', () => {
    tokenStorage.setTokens('access-token', 'refresh-token');
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate');

    http.get('/api/applications').subscribe({ error: () => {} });

    const req = httpMock.expectOne('/api/applications');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });
});
