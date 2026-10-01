import { Component, OnInit, inject, input, output } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { Profile, ProfileUpdate } from './profile.models';

/** Edit form for the profile header: name, headline, summary, contact, links, languages. */
@Component({
  selector: 'app-header-form',
  imports: [ReactiveFormsModule],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <div class="row g-3">
        <div class="col-md-6">
          <label class="form-label" for="h-name">Full name</label>
          <input class="form-control" id="h-name" formControlName="fullName" />
        </div>
        <div class="col-md-6">
          <label class="form-label" for="h-headline">Headline</label>
          <input class="form-control" id="h-headline" formControlName="headline" placeholder="e.g. Full-stack Java developer" />
        </div>
        <div class="col-md-6">
          <label class="form-label" for="h-phone">Phone</label>
          <input class="form-control" id="h-phone" formControlName="phone" />
        </div>
        <div class="col-md-6">
          <label class="form-label" for="h-location">Location</label>
          <input class="form-control" id="h-location" formControlName="location" />
        </div>
        <div class="col-12">
          <label class="form-label" for="h-summary">Summary</label>
          <textarea class="form-control" id="h-summary" rows="4" formControlName="summary"></textarea>
        </div>

        <div class="col-md-6" formArrayName="links">
          <label class="form-label d-block">Links</label>
          @for (link of links.controls; track $index) {
            <div class="input-group mb-2" [formGroupName]="$index">
              <input class="form-control" style="max-width: 32%" formControlName="label" placeholder="GitHub" aria-label="Link label" />
              <input class="form-control" formControlName="url" placeholder="https://…" aria-label="Link URL" />
              <button class="btn btn-outline-danger" type="button" (click)="links.removeAt($index)" aria-label="Remove link">
                <i class="bx bx-x"></i>
              </button>
            </div>
          }
          <button class="btn btn-sm btn-outline-primary" type="button" (click)="addLink()">
            <i class="bx bx-plus me-1"></i>Link
          </button>
        </div>

        <div class="col-md-6" formArrayName="languages">
          <label class="form-label d-block">Languages</label>
          @for (lang of languages.controls; track $index) {
            <div class="input-group mb-2" [formGroupName]="$index">
              <input class="form-control" formControlName="name" placeholder="French" aria-label="Language" />
              <input class="form-control" formControlName="level" placeholder="Native / C1 / B2" aria-label="Level" />
              <button class="btn btn-outline-danger" type="button" (click)="languages.removeAt($index)" aria-label="Remove language">
                <i class="bx bx-x"></i>
              </button>
            </div>
          }
          <button class="btn btn-sm btn-outline-primary" type="button" (click)="addLanguage()">
            <i class="bx bx-plus me-1"></i>Language
          </button>
        </div>
      </div>

      @if (form.invalid && form.touched) {
        <div class="alert alert-warning py-2 mt-3 mb-0">Each link needs a full http(s):// URL, and each language needs a name.</div>
      }
      @if (error()) {
        <div class="alert alert-danger py-2 mt-3 mb-0" role="alert">{{ error() }}</div>
      }

      <div class="mt-4 d-flex gap-2">
        <button class="btn btn-primary" type="submit" [disabled]="form.invalid || busy()">Save changes</button>
        <button class="btn btn-outline-secondary" type="button" (click)="cancelled.emit()">Cancel</button>
      </div>
    </form>
  `,
})
export class HeaderForm implements OnInit {
  readonly profile = input.required<Profile>();
  readonly busy = input(false);
  readonly error = input<string | null>(null);
  readonly saved = output<ProfileUpdate>();
  readonly cancelled = output<void>();

  private readonly fb = inject(FormBuilder).nonNullable;

  readonly form = this.fb.group({
    fullName: ['', Validators.maxLength(255)],
    headline: ['', Validators.maxLength(255)],
    summary: ['', Validators.maxLength(5000)],
    phone: ['', Validators.maxLength(50)],
    location: ['', Validators.maxLength(255)],
    links: this.fb.array<ReturnType<HeaderForm['linkGroup']>>([]),
    languages: this.fb.array<ReturnType<HeaderForm['languageGroup']>>([]),
  });

  get links(): FormArray {
    return this.form.controls.links;
  }

  get languages(): FormArray {
    return this.form.controls.languages;
  }

  ngOnInit(): void {
    const p = this.profile();
    this.form.patchValue({
      fullName: p.fullName ?? '',
      headline: p.headline ?? '',
      summary: p.summary ?? '',
      phone: p.phone ?? '',
      location: p.location ?? '',
    });
    p.links.forEach((l) => this.form.controls.links.push(this.linkGroup(l.label ?? '', l.url)));
    p.languages.forEach((l) => this.form.controls.languages.push(this.languageGroup(l.name, l.level ?? '')));
  }

  addLink(): void {
    this.form.controls.links.push(this.linkGroup('', ''));
  }

  addLanguage(): void {
    this.form.controls.languages.push(this.languageGroup('', ''));
  }

  private linkGroup(label: string, url: string) {
    return this.fb.group({
      label: [label, Validators.maxLength(50)],
      url: [url, [Validators.required, Validators.pattern(/^https?:\/\/\S+$/), Validators.maxLength(500)]],
    });
  }

  private languageGroup(name: string, level: string) {
    return this.fb.group({
      name: [name, [Validators.required, Validators.maxLength(60)]],
      level: [level, Validators.maxLength(60)],
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const orNull = (s: string) => s.trim() || null;
    this.saved.emit({
      fullName: orNull(v.fullName),
      headline: orNull(v.headline),
      summary: orNull(v.summary),
      phone: orNull(v.phone),
      location: orNull(v.location),
      links: v.links.map((l) => ({ label: orNull(l.label), url: l.url.trim() })),
      languages: v.languages.map((l) => ({ name: l.name.trim(), level: orNull(l.level) })),
    });
  }
}
