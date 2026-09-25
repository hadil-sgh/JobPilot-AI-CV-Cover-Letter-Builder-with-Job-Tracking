import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/http/api-error';

@Component({
  selector: 'app-register',
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
              <h4 class="mb-2">Your job hunt starts here 🚀</h4>
              <p class="mb-4">Upload your CV once, tailor it to every offer.</p>

              <form class="mb-3" [formGroup]="form" (ngSubmit)="submit()" novalidate>
                <div class="mb-3">
                  <label for="fullName" class="form-label">Full name</label>
                  <input id="fullName" type="text" class="form-control" formControlName="fullName"
                         placeholder="Enter your name" autocomplete="name" autofocus />
                </div>
                <div class="mb-3">
                  <label for="email" class="form-label">Email</label>
                  <input id="email" type="email" class="form-control" formControlName="email"
                         placeholder="Enter your email" autocomplete="email" />
                </div>
                <div class="mb-3 form-password-toggle">
                  <label class="form-label" for="password">Password</label>
                  <div class="input-group input-group-merge">
                    <input id="password" class="form-control" formControlName="password"
                           [type]="showPassword() ? 'text' : 'password'" autocomplete="new-password"
                           placeholder="&#xb7;&#xb7;&#xb7;&#xb7;&#xb7;&#xb7;&#xb7;&#xb7;&#xb7;&#xb7;&#xb7;&#xb7;" />
                    <span class="input-group-text cursor-pointer" role="button"
                          [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'"
                          (click)="showPassword.set(!showPassword())">
                      <i class="bx" [class.bx-hide]="!showPassword()" [class.bx-show]="showPassword()"></i>
                    </span>
                  </div>
                  <div class="form-text">At least 8 characters.</div>
                </div>

                @if (error()) {
                  <div class="alert alert-danger py-2" role="alert">{{ error() }}</div>
                }

                <button class="btn btn-primary d-grid w-100" type="submit" [disabled]="form.invalid || loading()">
                  {{ loading() ? 'Creating account…' : 'Sign up' }}
                </button>
              </form>

              <p class="text-center">
                <span>Already have an account? </span>
                <a routerLink="/login"><span>Sign in instead</span></a>
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPassword = signal(false);

  readonly form = inject(FormBuilder).nonNullable.group({
    fullName: ['', Validators.maxLength(255)],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });

  submit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set(null);
    this.auth.register(this.form.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
