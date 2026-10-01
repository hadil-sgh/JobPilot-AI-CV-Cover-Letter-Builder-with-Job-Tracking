import { NgTemplateOutlet } from '@angular/common';
import { Component, input, output } from '@angular/core';

import { dateRange } from '../profile/profile.models';
import { CvContent, CvEntry, ReviewFlag, SkillGroup, flagsFor, linesToBullets } from '../applications/applications.models';

/**
 * Editable CV sections. Facts (titles, organisations, dates, education, certifications) are shown
 * read-only: they come from the profile. Every change emits a new CvContent (immutable updates).
 */
@Component({
  selector: 'app-cv-editor',
  template: `
    @let c = cv();

    <!-- Summary -->
    <div class="card mb-4">
      <div class="card-header d-flex justify-content-between align-items-center">
        <h5 class="mb-0">Summary</h5>
        <button class="btn btn-sm btn-outline-primary" type="button" [disabled]="busy() !== null"
                (click)="regenerate.emit('summary')">
          @if (busy() === 'summary') { <span class="spinner-border spinner-border-sm me-1"></span> }
          @else { <i class="bx bx-refresh me-1"></i> }Regenerate
        </button>
      </div>
      <div class="card-body">
        <textarea class="form-control" rows="3" aria-label="Summary" [value]="c.summary ?? ''"
                  (input)="patch({ summary: value($event) })"></textarea>
        <ng-container *ngTemplateOutlet="flagsTpl; context: { $implicit: flags('summary') }" />
      </div>
    </div>

    <!-- Experience -->
    <div class="card mb-4">
      <h5 class="card-header">Experience</h5>
      <div class="card-body">
        @for (e of c.experience; track e.ref; let i = $index, first = $first, last = $last) {
          <div class="pb-3 mb-3" [class.border-bottom]="!last">
            <div class="d-flex justify-content-between align-items-start gap-2 mb-2">
              <div>
                <h6 class="mb-0">{{ e.title || '(untitled)' }} <span class="badge bg-label-secondary ms-1">{{ e.ref }}</span></h6>
                <small class="text-muted">{{ e.organization }}@if (e.organization && when(e)) { · }{{ when(e) }}</small>
              </div>
              <div class="d-flex gap-1 flex-shrink-0">
                <button class="btn btn-sm btn-icon btn-outline-secondary" type="button" title="Move up" aria-label="Move up"
                        [disabled]="first" (click)="moveExperience(i, -1)"><i class="bx bx-chevron-up"></i></button>
                <button class="btn btn-sm btn-icon btn-outline-secondary" type="button" title="Move down" aria-label="Move down"
                        [disabled]="last" (click)="moveExperience(i, 1)"><i class="bx bx-chevron-down"></i></button>
                <button class="btn btn-sm btn-outline-primary" type="button" [disabled]="busy() !== null"
                        (click)="regenerate.emit('experience:' + e.ref)">
                  @if (busy() === 'experience:' + e.ref) { <span class="spinner-border spinner-border-sm"></span> }
                  @else { <i class="bx bx-refresh"></i> }
                </button>
              </div>
            </div>
            <textarea class="form-control" rows="4" [attr.aria-label]="'Bullets for ' + e.title"
                      [value]="e.bullets.join('\\n')" (input)="setBullets('experience', i, value($event))"></textarea>
            <div class="form-text">One achievement per line · aim for ≤ 110 characters each.</div>
            <ng-container *ngTemplateOutlet="flagsTpl; context: { $implicit: flags('experience:' + e.ref) }" />
          </div>
        }
      </div>
    </div>

    <!-- Projects -->
    @if (c.projects.length) {
      <div class="card mb-4">
        <h5 class="card-header">Projects</h5>
        <div class="card-body">
          @for (p of c.projects; track p.ref; let i = $index, last = $last) {
            <div class="pb-3 mb-3" [class.border-bottom]="!last">
              <div class="d-flex justify-content-between align-items-start gap-2 mb-2">
                <h6 class="mb-0">{{ p.title }} <span class="badge bg-label-secondary ms-1">{{ p.ref }}</span></h6>
                <div class="d-flex gap-1">
                  <button class="btn btn-sm btn-outline-primary" type="button" [disabled]="busy() !== null"
                          (click)="regenerate.emit('projects:' + p.ref)">
                    @if (busy() === 'projects:' + p.ref) { <span class="spinner-border spinner-border-sm"></span> }
                    @else { <i class="bx bx-refresh"></i> }
                  </button>
                  <button class="btn btn-sm btn-icon btn-outline-danger" type="button" title="Remove from this CV"
                          aria-label="Remove project" (click)="removeProject(i)"><i class="bx bx-x"></i></button>
                </div>
              </div>
              <textarea class="form-control" rows="3" [attr.aria-label]="'Bullets for ' + p.title"
                        [value]="p.bullets.join('\\n')" (input)="setBullets('projects', i, value($event))"></textarea>
              <ng-container *ngTemplateOutlet="flagsTpl; context: { $implicit: flags('projects:' + p.ref) }" />
            </div>
          }
        </div>
      </div>
    }

    <!-- Skills -->
    <div class="card mb-4">
      <div class="card-header d-flex justify-content-between align-items-center">
        <h5 class="mb-0">Skills</h5>
        <button class="btn btn-sm btn-outline-primary" type="button" [disabled]="busy() !== null"
                (click)="regenerate.emit('skills')">
          @if (busy() === 'skills') { <span class="spinner-border spinner-border-sm me-1"></span> }
          @else { <i class="bx bx-refresh me-1"></i> }Regenerate
        </button>
      </div>
      <div class="card-body">
        @for (g of c.skills; track $index; let i = $index) {
          <div class="input-group mb-2">
            <input class="form-control" style="max-width: 30%" [value]="g.group" aria-label="Group name"
                   (input)="setSkillGroup(i, { group: value($event) })" />
            <input class="form-control" [value]="g.items.join(', ')" aria-label="Skills (comma separated)"
                   (change)="setSkillGroup(i, { items: commaList(value($event)) })" />
          </div>
        }
        <ng-container *ngTemplateOutlet="flagsTpl; context: { $implicit: flags('skills') }" />
      </div>
    </div>

    <!-- Read-only facts -->
    <div class="card mb-4">
      <h5 class="card-header">Education, certifications &amp; languages</h5>
      <div class="card-body small">
        <p class="text-muted mb-2"><i class="bx bx-lock-alt me-1"></i>These facts come from your profile. Edit them there.</p>
        <ul class="mb-0 ps-3">
          @for (e of c.education; track e.ref) { <li>{{ e.title }}@if (e.organization) { — {{ e.organization }} } {{ when(e) }}</li> }
          @for (e of c.certifications; track e.ref) { <li>{{ e.title }}@if (e.organization) { — {{ e.organization }} }</li> }
          @if (c.languages.length) {
            <li>Languages: @for (l of c.languages; track l.name; let last = $last) { {{ l.name }}@if (l.level) { ({{ l.level }}) }@if (!last) {, } }</li>
          }
        </ul>
      </div>
    </div>

    <ng-template #flagsTpl let-list>
      @if (list.length) {
        <div class="alert alert-warning py-2 mt-2 mb-0 small" role="alert">
          <i class="bx bx-error me-1"></i><strong>Please review:</strong>
          <ul class="mb-0 ps-3">@for (f of list; track $index) { <li>{{ f.message }}</li> }</ul>
        </div>
      }
    </ng-template>
  `,
  imports: [NgTemplateOutlet],
})
export class CvEditor {
  readonly cv = input.required<CvContent>();
  /** Section currently being regenerated (disables all regenerate buttons), or null. */
  readonly busy = input<string | null>(null);
  readonly changed = output<CvContent>();
  readonly regenerate = output<string>();

  flags(section: string): ReviewFlag[] {
    return flagsFor(this.cv().review, section);
  }

  when(e: CvEntry): string {
    return dateRange(e.start, e.end);
  }

  value(event: Event): string {
    return (event.target as HTMLInputElement | HTMLTextAreaElement).value;
  }

  commaList(text: string): string[] {
    return text.split(',').map((s) => s.trim()).filter(Boolean);
  }

  patch(change: Partial<CvContent>): void {
    this.changed.emit({ ...this.cv(), ...change });
  }

  setBullets(section: 'experience' | 'projects', index: number, text: string): void {
    const list = this.cv()[section].map((e, i) => (i === index ? { ...e, bullets: linesToBullets(text) } : e));
    this.patch({ [section]: list } as Partial<CvContent>);
  }

  moveExperience(index: number, delta: -1 | 1): void {
    const list = [...this.cv().experience];
    [list[index], list[index + delta]] = [list[index + delta], list[index]];
    this.patch({ experience: list });
  }

  removeProject(index: number): void {
    this.patch({ projects: this.cv().projects.filter((_, i) => i !== index) });
  }

  setSkillGroup(index: number, change: Partial<SkillGroup>): void {
    this.patch({ skills: this.cv().skills.map((g, i) => (i === index ? { ...g, ...change } : g)) });
  }
}
