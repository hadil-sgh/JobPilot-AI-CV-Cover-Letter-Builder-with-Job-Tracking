import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { errorMessage } from '../../core/http/api-error';
import { EvidenceReport, Job, JobSummary, coverage, matchLevel } from './jobs.models';
import { JobsService } from './jobs.service';

/**
 * Paste a job description → prompt-B analysis + evidence from the profile for each requirement.
 * JD text and LLM output are only ever interpolated (escaped), never bound as HTML.
 */
@Component({
  selector: 'app-analyze-page',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, DecimalPipe],
  template: `
    <div class="page-title-row">
      <h4 class="fw-bold py-1"><span class="text-muted fw-light">Jobs /</span> Analyze a job offer</h4>
    </div>

    <div class="row">
      <!-- Input -->
      <div class="col-xl-5 mb-4">
        <div class="card mb-4">
          <h5 class="card-header">Job description</h5>
          <div class="card-body">
            <form [formGroup]="form" (ngSubmit)="analyze()" novalidate>
              <div class="mb-3">
                <label class="form-label" for="jd">Paste the full offer (text or HTML)</label>
                <textarea id="jd" class="form-control" rows="14" formControlName="text" maxlength="100000"
                          placeholder="Paste the job description here…"></textarea>
                <div class="form-text">{{ form.controls.text.value.length | number }} characters · max 20,000 after cleanup</div>
              </div>
              <div class="mb-3">
                <label class="form-label" for="url">Link to the offer <small class="text-muted">(optional)</small></label>
                <input id="url" class="form-control" formControlName="sourceUrl" placeholder="https://…" />
              </div>
              @if (error()) {
                <div class="alert alert-danger py-2" role="alert">{{ error() }}</div>
              }
              <button class="btn btn-primary w-100" type="submit" [disabled]="form.invalid || analyzing()">
                @if (analyzing()) {
                  <span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>Analyzing with the local AI…
                } @else {
                  <i class="bx bx-analyse me-1"></i>Analyze
                }
              </button>
              @if (analyzing()) {
                <div class="form-text text-center mt-2">This usually takes 1–2 minutes. Keep this page open.</div>
              }
            </form>
          </div>
        </div>

        @if (recent().length) {
          <div class="card">
            <h5 class="card-header">Recent analyses</h5>
            <div class="list-group list-group-flush">
              @for (j of recent(); track j.id) {
                <button type="button" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center"
                        [class.active]="job()?.id === j.id" (click)="open(j)">
                  <span>
                    <span class="fw-semibold d-block">{{ j.title || 'Untitled offer' }}</span>
                    <small class="text-muted">{{ j.company || 'Unknown company' }} · {{ j.createdAt | date: 'mediumDate' }}</small>
                  </span>
                  <span class="badge bg-label-secondary text-uppercase">{{ j.language }}</span>
                </button>
              }
            </div>
          </div>
        }
      </div>

      <!-- Result -->
      <div class="col-xl-7">
        @if (job(); as j) {
          <div class="card mb-4">
            <div class="card-body">
              <div class="d-flex justify-content-between align-items-start flex-wrap gap-2">
                <div>
                  <h5 class="mb-1">{{ j.analysis.title || 'Untitled offer' }}</h5>
                  <p class="text-muted mb-2">{{ j.analysis.company || 'Company not stated' }}</p>
                </div>
                @if (coveragePct() !== null) {
                  <div class="text-end">
                    <div class="fs-4 fw-bold" [class.text-success]="coveragePct()! >= 70"
                         [class.text-warning]="coveragePct()! < 70">{{ coveragePct() }}%</div>
                    <small class="text-muted">must-haves with evidence</small>
                  </div>
                }
              </div>
              <div class="d-flex flex-wrap gap-2">
                <span class="badge bg-label-primary">{{ j.language === 'fr' ? 'French' : 'English' }}</span>
                @if (j.analysis.seniority) { <span class="badge bg-label-info">{{ j.analysis.seniority }}</span> }
                @if (j.analysis.tone) { <span class="badge bg-label-secondary text-none">Tone: {{ j.analysis.tone }}</span> }
                @if (j.sourceUrl) {
                  <a class="badge bg-label-dark text-none" [href]="j.sourceUrl" target="_blank" rel="noopener noreferrer">
                    <i class="bx bx-link-external me-1"></i>Offer
                  </a>
                }
              </div>
            </div>
          </div>

          @if (j.warnings.length) {
            <div class="alert alert-warning" role="alert">
              <h6 class="alert-heading mb-1"><i class="bx bx-shield-quarter me-1"></i>Suspicious text removed</h6>
              <ul class="mb-0 ps-3 small">
                @for (w of j.warnings; track $index) { <li>{{ w }}</li> }
              </ul>
            </div>
          }

          @if (evidence(); as ev) {
            @if (ev.profileChunks === 0) {
              <div class="alert alert-info" role="alert">
                Your profile index is empty, so no evidence can be found.
                <a routerLink="/profile">Import or fill in your profile</a> first.
              </div>
            }
          }

          <div class="card mb-4">
            <h5 class="card-header d-flex justify-content-between align-items-center">
              Requirements &amp; your evidence
              @if (loadingEvidence()) { <span class="spinner-border spinner-border-sm text-primary" role="status"></span> }
            </h5>
            <div class="table-responsive">
              <table class="table table-hover mb-0">
                <thead>
                  <tr><th>Requirement</th><th class="text-nowrap">Match</th></tr>
                </thead>
                <tbody>
                  @for (r of evidence()?.requirements ?? []; track $index) {
                    <tr class="cursor-pointer" (click)="toggle($index)">
                      <td>
                        <span class="badge me-2" [class]="r.mustHave ? 'bg-label-primary' : 'bg-label-secondary'">
                          {{ r.mustHave ? 'Must' : 'Nice' }}
                        </span>{{ r.requirement }}
                        @if (expanded() === $index) {
                          @for (e of r.evidence; track $index) {
                            <div class="border rounded p-2 mt-2 small bg-lighter">
                              <div class="d-flex justify-content-between mb-1">
                                <span class="badge bg-label-info">{{ e.type }}</span>
                                <span class="text-muted">score {{ e.score }} · similarity {{ e.similarity }}</span>
                              </div>
                              <div class="white-space-pre-line">{{ e.content }}</div>
                              @if (e.matchedTerms.length) {
                                <div class="mt-1">
                                  <small class="text-muted me-1">Matched:</small>
                                  @for (t of e.matchedTerms; track t) { <span class="badge bg-label-success text-none me-1">{{ t }}</span> }
                                </div>
                              }
                            </div>
                          } @empty {
                            <div class="small text-muted mt-2">Nothing in your profile matches this requirement.</div>
                          }
                        }
                      </td>
                      <td class="text-nowrap align-top">
                        <span class="badge" [class]="level(r.evidence[0]?.score).css">{{ level(r.evidence[0]?.score).label }}</span>
                      </td>
                    </tr>
                  } @empty {
                    @if (!loadingEvidence()) {
                      <tr><td colspan="2" class="text-muted">No requirements were found in this offer.</td></tr>
                    }
                  }
                </tbody>
              </table>
            </div>
          </div>

          <div class="row">
            <div class="col-md-6 mb-4">
              <div class="card h-100">
                <h5 class="card-header">Keywords</h5>
                <div class="card-body d-flex flex-wrap gap-1">
                  @for (k of j.analysis.keywords; track k) { <span class="badge bg-label-primary text-none">{{ k }}</span> }
                  @empty { <span class="text-muted small">None found.</span> }
                </div>
              </div>
            </div>
            <div class="col-md-6 mb-4">
              <div class="card h-100">
                <h5 class="card-header">Responsibilities</h5>
                <div class="card-body">
                  <ul class="ps-3 mb-0 small">
                    @for (r of j.analysis.responsibilities; track $index) { <li>{{ r }}</li> }
                    @empty { <li class="text-muted">None found.</li> }
                  </ul>
                </div>
              </div>
            </div>
          </div>
        } @else {
          <div class="card">
            <div class="card-body text-center py-5">
              <span class="avatar avatar-xl mx-auto mb-3 d-block">
                <span class="avatar-initial rounded-circle bg-label-primary"><i class="bx bx-search-alt bx-md"></i></span>
              </span>
              <h5>See how you match an offer</h5>
              <p class="text-muted mb-0">
                Paste a job description: the local AI extracts its requirements and keywords, then JobPilot
                finds the evidence for each one in your profile.
              </p>
            </div>
          </div>
        }
      </div>
    </div>
  `,
})
export class AnalyzePage {
  private readonly jobs = inject(JobsService);

  readonly form = inject(FormBuilder).nonNullable.group({
    text: ['', [Validators.required, Validators.minLength(100), Validators.maxLength(100000)]],
    sourceUrl: ['', [Validators.pattern(/^https?:\/\/\S+$/), Validators.maxLength(1000)]],
  });

  readonly job = signal<Job | null>(null);
  readonly evidence = signal<EvidenceReport | null>(null);
  readonly recent = signal<JobSummary[]>([]);
  readonly analyzing = signal(false);
  readonly loadingEvidence = signal(false);
  readonly error = signal<string | null>(null);
  readonly expanded = signal<number | null>(null);
  readonly coveragePct = computed(() => coverage(this.evidence()));
  readonly level = matchLevel;

  constructor() {
    this.loadRecent();
  }

  analyze(): void {
    if (this.form.invalid) return;
    const { text, sourceUrl } = this.form.getRawValue();
    this.analyzing.set(true);
    this.error.set(null);
    this.jobs.analyze(text, sourceUrl.trim() || null).subscribe({
      next: (job) => {
        this.analyzing.set(false);
        this.show(job);
        this.loadRecent();
      },
      error: (err) => {
        this.analyzing.set(false);
        this.error.set(errorMessage(err, 'The analysis failed. Please try again.'));
      },
    });
  }

  open(summary: JobSummary): void {
    this.jobs.get(summary.id).subscribe({
      next: (job) => this.show(job),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  toggle(index: number): void {
    this.expanded.set(this.expanded() === index ? null : index);
  }

  private show(job: Job): void {
    this.job.set(job);
    this.evidence.set(null);
    this.expanded.set(null);
    this.loadingEvidence.set(true);
    this.jobs.evidence(job.id).subscribe({
      next: (ev) => {
        this.evidence.set(ev);
        this.loadingEvidence.set(false);
      },
      error: (err) => {
        this.loadingEvidence.set(false);
        this.error.set(errorMessage(err, 'Could not load the evidence from your profile.'));
      },
    });
  }

  private loadRecent(): void {
    this.jobs.recent().subscribe({ next: (r) => this.recent.set(r), error: () => undefined });
  }
}
