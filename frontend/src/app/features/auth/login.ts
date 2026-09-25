import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/http/api-error';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="container-xxl">
      <div class="authentication-wrapper authentication-basic container-p-y">
        <div class="authentication-inner">
          <div class="card">
            <div class="card-body">
              <div class="app-brand justify-content-center">
                <a routerLink="/login" class="app-brand-link gap-2">
                  <span class="app-brand-logo demo"><span class="app-brand-mark bg-primary"><i class="bx bxs-paper-plane"></i></span></span>
                  <span class="app-brand-text demo text-body fw-bolder text-capitalize">JobPilot</span>
                </a>
              </div>
              <h4 class="mb-2">Welcome back! 👋</h4>
              <p class="mb-4">Sign in to tailor your next application.</p>

              <form class="mb-3" [formGroup]="form" (ngSubmit)="submit()" novalidate>
                <div class="mb-3">
                  <label for="email" class="form-label">Email</label>
                  <input id="email" type="email" class="form-control" formControlName="email"
                         placeholder="Enter your email" autocomplete="email" autofocus />
                </div>
                <div class="mb-3 form-password-toggle">
                  <label class="form-label" for="password">Password</label>
                  <div class="input-group input-group-merge">
                    <input id="password" class="form-control" formControlName="password"
                           [type]="showPassword() ? 'text' : 'password'" autocomplete="current-password"
                           placeholder="&#xb7;&#xb7;&#xb7;&#xb7;&#xb7;&#xb7;&#xb7;&#xb7;&#xb7;&#xb7;&#xb7;&#xb7;" />
                    <span class="input-group-text cursor-pointer" role="button"
                          [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'"
                          (click)="showPassword.set(!showPassword())">
                      <i class="bx" [class.bx-hide]="!showPassword()" [class.bx-show]="showPassword()"></i>
                    </span>
                  </div>
                </div>

                @if (error()) {
                  <div class="alert alert-danger py-2" role="alert">{{ error() }}</div>
                }

                <button class="btn btn-primary d-grid w-100" type="submit" [disabled]="form.invalid || loading()">
                  {{ loading() ? 'Signing in…' : 'Sign in' }}
                </button>
              </form>

              <p class="text-center">
                <span>New on JobPilot? </span>
                <a routerLink="/register"><span>Create an account</span></a>
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPassword = signal(false);

  readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl(safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'))),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}

/** Only allow in-app paths so ?returnUrl= cannot redirect to another site. */
export function safeReturnUrl(url: string | null): string {
  return url && url.startsWith('/') && !url.startsWith('//') ? url : '/';
}
