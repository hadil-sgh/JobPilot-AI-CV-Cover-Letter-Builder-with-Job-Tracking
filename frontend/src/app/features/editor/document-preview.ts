import { NgTemplateOutlet } from '@angular/common';
import { Component, input } from '@angular/core';

import { dateRange } from '../profile/profile.models';
import { CvContent, CvEntry, LetterContent } from '../applications/applications.models';

/**
 * Plain HTML preview of the current content (Phase 5 replaces it with the compiled LaTeX PDF).
 * Everything is interpolated, so model output is always escaped — never bound as HTML.
 */
@Component({
  selector: 'app-document-preview',
  styles: `
    .paper { background: #fff; border: 1px solid #e4e6e8; border-radius: 0.375rem; padding: 2rem;
             font-family: 'Public Sans', serif; color: #2b2c40; font-size: 0.82rem; line-height: 1.45; }
    .paper h3 { font-size: 1.25rem; margin: 0; }
    .paper h4 { font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.06em; border-bottom: 1px solid #d9dee3;
                padding-bottom: 2px; margin: 1rem 0 0.4rem; }
    .paper .row-line { display: flex; justify-content: space-between; gap: 1rem; font-weight: 600; }
    .paper ul { margin: 0.2rem 0 0.5rem; padding-left: 1.1rem; }
    .paper p { margin-bottom: 0.7rem; }
  `,
  template: `
    @if (cv(); as c) {
      <div class="paper">
        <h3>{{ c.header.fullName }}</h3>
        @if (c.header.headline) { <div class="text-muted">{{ c.header.headline }}</div> }
        <div class="small mt-1">
          {{ c.header.email }}@if (c.header.phone) { · {{ c.header.phone }} }@if (c.header.location) { · {{ c.header.location }} }
          @for (l of c.header.links; track l.url) { · {{ l.url }} }
        </div>
        @if (c.summary) { <h4>{{ fr() ? 'Profil' : 'Summary' }}</h4><div>{{ c.summary }}</div> }
        <h4>{{ fr() ? 'Expérience' : 'Experience' }}</h4>
        @for (e of c.experience; track e.ref) { <ng-container *ngTemplateOutlet="entry; context: { $implicit: e }" /> }
        @if (c.projects.length) {
          <h4>{{ fr() ? 'Projets' : 'Projects' }}</h4>
          @for (e of c.projects; track e.ref) { <ng-container *ngTemplateOutlet="entry; context: { $implicit: e }" /> }
        }
        @if (c.education.length) {
          <h4>{{ fr() ? 'Formation' : 'Education' }}</h4>
          @for (e of c.education; track e.ref) { <ng-container *ngTemplateOutlet="entry; context: { $implicit: e }" /> }
        }
        @if (c.skills.length) {
          <h4>{{ fr() ? 'Compétences' : 'Skills' }}</h4>
          @for (g of c.skills; track $index) { <div><strong>{{ g.group }}:</strong> {{ g.items.join(', ') }}</div> }
        }
        @if (c.certifications.length) {
          <h4>Certifications</h4>
          @for (e of c.certifications; track e.ref) { <div>{{ e.title }}@if (e.organization) { — {{ e.organization }} }</div> }
        }
        @if (c.languages.length) {
          <h4>{{ fr() ? 'Langues' : 'Languages' }}</h4>
          <div>@for (l of c.languages; track l.name; let last = $last) { {{ l.name }}@if (l.level) { ({{ l.level }}) }@if (!last) { · } }</div>
        }
      </div>
    }
    @if (letter(); as l) {
      <div class="paper">
        <p class="text-end small">{{ l.company }}@if (l.roleTitle) { — {{ l.roleTitle }} }</p>
        <p>{{ l.greeting }}</p>
        @for (p of l.paragraphs; track $index) { <p>{{ p }}</p> }
        <p>{{ l.closing }}</p>
        <p class="fw-semibold">{{ l.signature }}</p>
      </div>
    }

    <ng-template #entry let-e>
      <div class="row-line"><span>{{ e.title }}@if (e.organization) { — {{ e.organization }} }</span>
        <span class="fw-normal text-muted">{{ when(e) }}</span></div>
      @if (e.bullets.length) { <ul>@for (b of e.bullets; track $index) { <li>{{ b }}</li> }</ul> }
    </ng-template>
  `,
  imports: [NgTemplateOutlet],
})
export class DocumentPreview {
  readonly cv = input<CvContent | null>(null);
  readonly letter = input<LetterContent | null>(null);

  fr(): boolean {
    return this.cv()?.language === 'fr';
  }

  when(e: CvEntry): string {
    return dateRange(e.start, e.end);
  }
}

