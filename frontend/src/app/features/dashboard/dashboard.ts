import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { Application } from '../applications/applications.models';
import { ApplicationsService } from '../applications/applications.service';

type Health = 'checking' | 'up' | 'down';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DatePipe],
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

    <div class="card">
      <div class="card-header d-flex justify-content-between align-items-center">
        <h5 class="mb-0">Recent applications</h5>
        <a routerLink="/jobs/analyze" class="btn btn-sm btn-primary"><i class="bx bx-plus me-1"></i>New application</a>
      </div>
      @if (applications().length) {
        <div class="table-responsive">
          <table class="table table-hover mb-0">
            <thead><tr><th>Role</th><th>Company</th><th>Created</th><th></th></tr></thead>
            <tbody>
              @for (a of applications(); track a.id) {
                <tr>
                  <td class="fw-semibold">{{ a.roleTitle || 'Untitled role' }}</td>
                  <td>{{ a.company }}</td>
                  <td class="text-muted">{{ a.createdAt | date: 'mediumDate' }}</td>
                  <td class="text-end"><a class="btn btn-sm btn-outline-primary" [routerLink]="['/applications', a.id]">Open editor</a></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <div class="card-body text-muted">No applications yet. Analyze a job offer to create your first one.</div>
      }
    </div>
  `,
})
export class Dashboard {
  readonly auth = inject(AuthService);
  readonly health = signal<Health>('checking');
  readonly applications = signal<Application[]>([]);

  constructor() {
    inject(ApplicationsService).recent().subscribe({ next: (a) => this.applications.set(a), error: () => undefined });
    inject(HttpClient)
      .get<{ status: string }>('/actuator/health')
      .subscribe({
        next: (res) => this.health.set(res.status === 'UP' ? 'up' : 'down'),
        error: () => this.health.set('down'),
      });
  }
}
