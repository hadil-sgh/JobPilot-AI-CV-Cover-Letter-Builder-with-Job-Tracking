import { DOCUMENT } from '@angular/common';
import { Component, HostListener, computed, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { AuthService } from '../core/auth/auth.service';

interface NavItem {
  label: string;
  path: string;
  icon: string; // Boxicons class
  phase?: number; // not built yet: shown disabled with the phase that delivers it
}

/**
 * App frame using Sneat's vertical menu + detached navbar markup. Sneat's own JS (menu.js,
 * Bootstrap dropdowns) is not loaded; the mobile menu and user dropdown are driven by signals.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="layout-wrapper layout-content-navbar">
      <div class="layout-container">
        <aside id="layout-menu" class="layout-menu menu-vertical menu bg-menu-theme">
          <div class="app-brand demo">
            <a routerLink="/" class="app-brand-link">
              <span class="app-brand-logo demo"><span class="app-brand-mark bg-primary"><i class="bx bxs-paper-plane"></i></span></span>
              <span class="app-brand-text demo menu-text fw-bolder ms-2 text-capitalize">JobPilot</span>
            </a>
            <a href="#" class="layout-menu-toggle menu-link text-large ms-auto d-block d-xl-none"
               (click)="$event.preventDefault(); menuOpen.set(false)">
              <i class="bx bx-chevron-left bx-sm align-middle"></i>
            </a>
          </div>

          <div class="menu-inner-shadow"></div>

          <ul class="menu-inner py-1">
            @for (item of nav; track item.path) {
              @if (item.phase) {
                <li class="menu-item menu-disabled" [title]="'Coming in phase ' + item.phase">
                  <span class="menu-link">
                    <i class="menu-icon tf-icons bx" [class]="item.icon"></i>
                    <div>{{ item.label }}</div>
                    <span class="badge rounded-pill bg-label-secondary ms-auto">P{{ item.phase }}</span>
                  </span>
                </li>
              } @else {
                <li class="menu-item" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: item.path === '/' }">
                  <a [routerLink]="item.path" class="menu-link">
                    <i class="menu-icon tf-icons bx" [class]="item.icon"></i>
                    <div>{{ item.label }}</div>
                  </a>
                </li>
              }
            }
          </ul>
        </aside>

        <div class="layout-page">
          <nav class="layout-navbar container-xxl navbar navbar-expand-xl navbar-detached align-items-center bg-navbar-theme"
               id="layout-navbar">
            <div class="layout-menu-toggle navbar-nav align-items-xl-center me-3 me-xl-0 d-xl-none">
              <a class="nav-item nav-link px-0 me-xl-4" href="#" (click)="$event.preventDefault(); menuOpen.set(true)">
                <i class="bx bx-menu bx-sm"></i>
              </a>
            </div>

            <div class="navbar-nav-right d-flex align-items-center" id="navbar-collapse">
              <ul class="navbar-nav flex-row align-items-center ms-auto">
                <li class="nav-item navbar-dropdown dropdown-user dropdown position-relative">
                  <a class="nav-link dropdown-toggle hide-arrow" href="#" aria-label="Account menu"
                     (click)="$event.preventDefault(); $event.stopPropagation(); userMenuOpen.set(!userMenuOpen())">
                    <div class="avatar avatar-online">
                      <span class="avatar-initial rounded-circle bg-label-primary">{{ initials() }}</span>
                    </div>
                  </a>
                  <ul class="dropdown-menu dropdown-menu-end" [class.show]="userMenuOpen()">
                    <li>
                      <div class="dropdown-item">
                        <div class="d-flex">
                          <div class="flex-shrink-0 me-3">
                            <div class="avatar avatar-online">
                              <span class="avatar-initial rounded-circle bg-label-primary">{{ initials() }}</span>
                            </div>
                          </div>
                          <div class="flex-grow-1">
                            <span class="fw-semibold d-block">{{ displayName() }}</span>
                            <small class="text-muted">{{ auth.user()?.email }}</small>
                          </div>
                        </div>
                      </div>
                    </li>
                    <li><div class="dropdown-divider"></div></li>
                    <li>
                      <a class="dropdown-item" routerLink="/profile" (click)="userMenuOpen.set(false)">
                        <i class="bx bx-user me-2"></i><span class="align-middle">My Profile</span>
                      </a>
                    </li>
                    <li><div class="dropdown-divider"></div></li>
                    <li>
                      <a class="dropdown-item" href="#" (click)="$event.preventDefault(); auth.logout()">
                        <i class="bx bx-power-off me-2"></i><span class="align-middle">Log Out</span>
                      </a>
                    </li>
                  </ul>
                </li>
              </ul>
            </div>
          </nav>

          <div class="content-wrapper">
            <div class="container-xxl flex-grow-1 container-p-y">
              <router-outlet />
            </div>
            <div class="content-backdrop fade"></div>
          </div>
        </div>
      </div>

      <div class="layout-overlay layout-menu-toggle" (click)="menuOpen.set(false)"></div>
    </div>
  `,
})
export class Shell {
  readonly auth = inject(AuthService);
  private readonly document = inject(DOCUMENT);

  readonly menuOpen = signal(false);
  readonly userMenuOpen = signal(false);

  readonly displayName = computed(() => {
    const user = this.auth.user();
    return user?.fullName || user?.email || '';
  });

  readonly initials = computed(() =>
    this.displayName()
      .split(/[\s@.]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]!.toUpperCase())
      .join(''),
  );

  readonly nav: NavItem[] = [
    { label: 'Dashboard', path: '/', icon: 'bx-home-circle' },
    { label: 'Profile', path: '/profile', icon: 'bx-user' },
    { label: 'New application', path: '/jobs/analyze', icon: 'bx-plus-circle' },
    { label: 'Applications', path: '/applications', icon: 'bx-table', phase: 6 },
    { label: 'Settings', path: '/settings', icon: 'bx-cog', phase: 7 },
  ];

  constructor() {
    // Sneat's CSS opens the mobile menu when <html> has .layout-menu-expanded.
    effect(() => this.document.documentElement.classList.toggle('layout-menu-expanded', this.menuOpen()));
    inject(Router)
      .events.pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => {
        this.menuOpen.set(false);
        this.userMenuOpen.set(false);
      });
  }

  @HostListener('document:click')
  closeUserMenu(): void {
    this.userMenuOpen.set(false);
  }
}
