import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth/auth.service';

interface NavItem {
  label: string;
  path: string;
  icon: string; // SVG path data (static, authored here — never user content)
  phase?: number; // not built yet: shown disabled with the phase that delivers it
}

/** App frame: slim icon rail on the left (style reference: docs/style-reference.png). */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="shell">
      <aside class="rail">
        <a class="rail-brand" routerLink="/" title="JobPilot">JP</a>
        <nav>
          @for (item of nav; track item.path) {
            @if (item.phase) {
              <span class="rail-item disabled" [title]="item.label + ' (coming in phase ' + item.phase + ')'">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path [attr.d]="item.icon" /></svg>
                <span class="sr-only">{{ item.label }} (coming soon)</span>
              </span>
            } @else {
              <a class="rail-item" [routerLink]="item.path" routerLinkActive="active" [title]="item.label"
                 [routerLinkActiveOptions]="{ exact: item.path === '/' }">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path [attr.d]="item.icon" /></svg>
                <span class="sr-only">{{ item.label }}</span>
              </a>
            }
          }
        </nav>
        <button class="rail-item rail-bottom" type="button" title="Log out" (click)="auth.logout()">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 12H4m0 0l4-4m-4 4l4 4M13 4h6v16h-6" /></svg>
          <span class="sr-only">Log out</span>
        </button>
      </aside>

      <div class="main">
        <header class="topbar">
          <div class="user">
            <span class="avatar" aria-hidden="true">{{ initials() }}</span>
            <span class="user-name">{{ displayName() }}</span>
          </div>
        </header>
        <main class="content">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class Shell {
  readonly auth = inject(AuthService);

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
    { label: 'Dashboard', path: '/', icon: 'M3 11l9-7 9 7M5 10v10h5v-6h4v6h5V10' },
    { label: 'Profile', path: '/profile', icon: 'M12 12a4 4 0 100-8 4 4 0 000 8zm-7 9a7 7 0 0114 0', phase: 2 },
    { label: 'New application', path: '/new', icon: 'M12 5v14M5 12h14', phase: 4 },
    { label: 'Applications', path: '/applications', icon: 'M4 6h16M4 12h16M4 18h10', phase: 6 },
    { label: 'Settings', path: '/settings', icon: 'M4 7h10m4 0h2M4 17h4m4 0h8M16 5v4M10 15v4', phase: 7 },
  ];
}
