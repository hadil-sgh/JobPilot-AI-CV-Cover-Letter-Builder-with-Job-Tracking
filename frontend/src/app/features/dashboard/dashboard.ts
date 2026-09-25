import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';

type Health = 'checking' | 'up' | 'down';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  template: `
    <div class="row">
      <div class="col-lg-8 mb-4">
        <div class="card">
          <div class="card-body">
            <h5 class="card-title text-primary">Welcome, {{ auth.user()?.fullName || auth.user()?.email }}! 🎉</h5>
            <p class="mb-4">
              Start by importing your CV: JobPilot turns it into a structured master profile that every
              tailored CV and letter is built from.
            </p>
            <a routerLink="/profile" class="btn btn-sm btn-outline-primary">Build my profile</a>
          </div>
        </div>
      </div>

      <div class="col-lg-4 mb-4">
        <div class="card h-100">
          <div class="card-body">
            <div class="d-flex align-items-center mb-3">
              <div class="avatar flex-shrink-0 me-3">
                <span class="avatar-initial rounded bg-label-success"><i class="bx bx-server"></i></span>
              </div>
              <span class="fw-semibold">System status</span>
            </div>
            <div class="d-flex justify-content-between align-items-center">
              <span>Backend API</span>
              @switch (health()) {
                @case ('up') { <span class="badge bg-label-success">Up</span> }
                @case ('down') { <span class="badge bg-label-danger">Down</span> }
                @default { <span class="badge bg-label-secondary">Checking</span> }
              }
            </div>
          </div>
        </div>
      </div>
    </div>
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
