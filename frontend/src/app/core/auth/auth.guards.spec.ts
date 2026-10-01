import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { safeReturnUrl } from '../../features/auth/login';
import { authGuard, guestGuard } from './auth.guards';
import { AuthService } from './auth.service';

describe('auth guards', () => {
  const loggedIn = signal(false);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { isAuthenticated: loggedIn } }],
    });
  });

  const run = (guard: typeof authGuard, url = '/applications') =>
    TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );

  it('authGuard redirects anonymous users to /login with returnUrl', () => {
    loggedIn.set(false);
    const result = run(authGuard) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/login?returnUrl=%2Fapplications');
  });

  it('authGuard lets logged-in users through', () => {
    loggedIn.set(true);
    expect(run(authGuard)).toBeTrue();
  });

  it('guestGuard sends logged-in users home', () => {
    loggedIn.set(true);
    expect(TestBed.inject(Router).serializeUrl(run(guestGuard) as UrlTree)).toBe('/');
  });

  it('safeReturnUrl rejects external redirects', () => {
    expect(safeReturnUrl('/applications')).toBe('/applications');
    expect(safeReturnUrl('//evil.com')).toBe('/');
    expect(safeReturnUrl('https://evil.com')).toBe('/');
    expect(safeReturnUrl(null)).toBe('/');
  });
});
