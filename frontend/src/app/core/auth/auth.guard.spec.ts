import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { authGuard } from './auth.guard';
import { TokenStorageService } from './token-storage.service';

describe('authGuard', () => {
  let tokenStorage: TokenStorageService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: { createUrlTree: (commands: unknown[]) => ({ commands }) } },
      ],
    });
    tokenStorage = TestBed.inject(TokenStorageService);
    router = TestBed.inject(Router);
    localStorage.clear();
  });

  afterEach(() => localStorage.clear());

  function runGuard() {
    return TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/dashboard' } as never),
    );
  }

  it('allows navigation when an access token is present', () => {
    tokenStorage.setTokens('access-token', 'refresh-token');

    expect(runGuard()).toBe(true);
  });

  it('redirects to /login when there is no access token', () => {
    const createUrlTreeSpy = spyOn(router, 'createUrlTree').and.callThrough();

    runGuard();

    expect(createUrlTreeSpy).toHaveBeenCalledWith(['/login']);
  });
});
