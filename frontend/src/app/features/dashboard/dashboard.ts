import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';

type Health = 'checking' | 'up' | 'down';

@Component({
  selector: 'app-dashboard',
  template: `
    <div class="page-header">
      <h1>Dashboard</h1>
      <div class="page-actions">
        <button class="btn btn-outline" type="button" disabled title="Coming in phase 4">New application</button>
      </div>
    </div>

    <section class="card">
      <h2>Hi {{ auth.user()?.fullName || auth.user()?.email }}</h2>
      <p class="muted">
        Your account is ready. Next up: upload your CV to build your master profile (Phase 2).
      </p>
    </section>

    <section class="card">
      <h2>System status</h2>
      <div class="status-row">
        <span>Backend API</span>
        <span class="pill" [class.pill-green]="health() === 'up'" [class.pill-red]="health() === 'down'"
              [class.pill-gray]="health() === 'checking'">
          {{ health() === 'checking' ? 'Checking' : health() === 'up' ? 'Up' : 'Down' }}
        </span>
      </div>
    </section>
  `,
})
export class Dashboard {
  readonly auth = inject(AuthService);
  readonly health = signal<Health>('checking');

  constructor() {
    inject(HttpClient)
      .get<{ status: string }>('/actuator/health')
      .subscribe({
        next: (res) => this.health.set(res.status === 'UP' ? 'up' : 'down'),
        error: () => this.health.set('down'),
      });
  }
}
