import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription, filter, switchMap, take, timer } from 'rxjs';

import { errorMessage } from '../../core/http/api-error';
import {
  Application,
  CvContent,
  GeneratedDocument,
  GenerationJob,
  LetterContent,
  STEPS,
  progressOf,
} from '../applications/applications.models';
import { ApplicationsService } from '../applications/applications.service';
import { CvEditor } from './cv-editor';
import { DocumentPreview } from './document-preview';
import { LetterEditor } from './letter-editor';
import { PdfPanel } from './pdf-panel';

type Tab = 'cv' | 'letter';
const POLL_MS = 3000;

/**
 * Application editor (PROJECT.md 2.7 page 4): generation progress, match score + gaps, CV and
 * letter editors with per-section regenerate, live preview.
 */
@Component({
  selector: 'app-editor-page',
  imports: [RouterLink, CvEditor, LetterEditor, DocumentPreview, PdfPanel],
  template: `
    @if (app(); as a) {
      <div class="page-title-row">
        <h4 class="fw-bold py-1 mb-0">
          <span class="text-muted fw-light">Applications /</span> {{ a.roleTitle || 'Application' }}
          <span class="text-muted fw-light"> at {{ a.company }}</span>
        </h4>
        <div class="d-flex gap-2">
          @if (dirty()) {
            <button class="btn btn-primary" type="button" [disabled]="saving()" (click)="save()">
              @if (saving()) { <span class="spinner-border spinner-border-sm me-1"></span> } @else { <i class="bx bx-save me-1"></i> }
              Save changes
            </button>
          }
          <button class="btn btn-outline-primary" type="button" [disabled]="generating() || dirty()" (click)="generate()"
                  [title]="dirty() ? 'Save or discard your edits first' : ''">
            <i class="bx bx-magic-wand me-1"></i>{{ cv() ? 'Generate a new version' : 'Generate CV & letter' }}
          </button>
        </div>
      </div>
    }

    @if (error()) {
      <div class="alert alert-danger alert-dismissible" role="alert">
        {{ error() }}
        <button type="button" class="btn-close" aria-label="Close" (click)="error.set(null)"></button>
      </div>
    }

    <!-- Generation progress -->
    @if (generating()) {
      <div class="card mb-4">
        <div class="card-body">
          <div class="d-flex justify-content-between mb-2">
            <span class="fw-semibold"><span class="spinner-border spinner-border-sm text-primary me-2"></span>
              {{ job()?.status === 'QUEUED' ? 'Waiting for the AI…' : stepLabel() }}</span>
            <small class="text-muted">usually 4–8 minutes on a local GPU · you can leave this page</small>
          </div>
          <div class="progress" style="height: 8px">
            <div class="progress-bar progress-bar-striped progress-bar-animated" role="progressbar"
                 [style.width.%]="progress()" [attr.aria-valuenow]="progress()" aria-valuemin="0" aria-valuemax="100"></div>
          </div>
          <ul class="list-unstyled small mt-3 mb-0">
            @for (s of steps; track s.key; let i = $index) {
              <li [class.text-muted]="i > stepIndex()">
                <i class="bx me-1" [class.bx-check-circle]="i < stepIndex()" [class.text-success]="i < stepIndex()"
                   [class.bx-loader-alt]="i === stepIndex()" [class.bx-spin]="i === stepIndex()"
                   [class.bx-circle]="i > stepIndex()"></i>{{ s.label }}
              </li>
            }
          </ul>
        </div>
      </div>
    }
    @if (job()?.status === 'FAILED') {
      <div class="alert alert-danger" role="alert">
        <strong>The last generation failed:</strong> {{ job()?.error }}
      </div>
    }

    @if (cv(); as c) {
      <!-- Match -->
      @if (c.match; as m) {
        <div class="card mb-4">
          <div class="card-body d-flex flex-wrap gap-4 align-items-center">
            <div class="text-center" style="min-width: 110px">
              <div class="display-6 fw-bold" [class.text-success]="m.score >= 70" [class.text-warning]="m.score >= 40 && m.score < 70"
                   [class.text-danger]="m.score < 40">{{ m.score }}%</div>
              <small class="text-muted">match score</small>
            </div>
            <div class="flex-grow-1">
              <h6 class="mb-2">Skill gaps</h6>
              @for (g of m.gaps; track $index) { <span class="badge bg-label-danger text-none me-1 mb-1">{{ g }}</span> }
              @empty { <span class="text-success small"><i class="bx bx-check me-1"></i>No gaps found for this offer.</span> }
              <div class="small text-muted mt-2">
                @for (r of m.requirements; track $index) {
                  <span class="me-2"><i class="bx" [class.bx-check]="r.verdict === 'yes'" [class.text-success]="r.verdict === 'yes'"
                     [class.bx-minus]="r.verdict === 'partial'" [class.text-warning]="r.verdict === 'partial'"
                     [class.bx-x]="r.verdict === 'no'" [class.text-danger]="r.verdict === 'no'"></i>{{ short(r.requirement) }}</span>
                }
              </div>
            </div>
          </div>
        </div>
      }

      @if (reviewCount() > 0) {
        <div class="alert alert-warning" role="alert">
          <i class="bx bx-shield-quarter me-1"></i>The fact checker found <strong>{{ reviewCount() }}</strong>
          statement(s) it could not verify against your profile. They are highlighted below — fix or remove them before sending.
        </div>
      }

      <ul class="nav nav-tabs mb-3" role="tablist">
        <li class="nav-item"><button class="nav-link" [class.active]="tab() === 'cv'" type="button" role="tab" (click)="tab.set('cv')">
          <i class="bx bx-file me-1"></i>CV <span class="text-muted small">v{{ cvDoc()?.version }}</span></button></li>
        <li class="nav-item"><button class="nav-link" [class.active]="tab() === 'letter'" type="button" role="tab" (click)="tab.set('letter')">
          <i class="bx bx-envelope me-1"></i>Motivation letter</button></li>
      </ul>

      <div class="row">
        <div class="col-xl-7">
          @if (tab() === 'cv') {
            <app-cv-editor [cv]="c" [busy]="busySection()" (changed)="editCv($event)" (regenerate)="regenerate('cv', $event)" />
          } @else if (letter(); as l) {
            <app-letter-editor [letter]="l" [busy]="busySection() === 'letter'" (changed)="editLetter($event)"
                               (regenerate)="regenerate('letter', $event)" />
          }
        </div>
        <div class="col-xl-5">
          <div class="position-sticky" style="top: 1rem">
            <div class="btn-group btn-group-sm mb-2" role="group" aria-label="Preview mode">
              <button type="button" class="btn" [class.btn-primary]="preview() === 'live'" [class.btn-outline-primary]="preview() !== 'live'"
                      (click)="preview.set('live')">Live preview</button>
              <button type="button" class="btn" [class.btn-primary]="preview() === 'pdf'" [class.btn-outline-primary]="preview() !== 'pdf'"
                      (click)="preview.set('pdf')"><i class="bx bxs-file-pdf me-1"></i>PDF &amp; ATS</button>
            </div>
            @if (preview() === 'live') {
              @if (tab() === 'cv') { <app-document-preview [cv]="c" /> } @else { <app-document-preview [letter]="letter()" /> }
            } @else {
              @if (tab() === 'cv' && cvDoc(); as d) {
                <app-pdf-panel [doc]="d" [dirty]="dirty()" [fileName]="pdfName('CV')" (rendered)="onRendered($event)" />
              } @else if (letterDoc(); as d) {
                <app-pdf-panel [doc]="d" [dirty]="dirty()" [fileName]="pdfName('Letter')" (rendered)="onRendered($event)" />
              }
            }
          </div>
        </div>
      </div>
    } @else if (!generating() && loaded()) {
      <div class="card">
        <div class="card-body text-center py-5">
          <span class="avatar avatar-xl mx-auto mb-3 d-block">
            <span class="avatar-initial rounded-circle bg-label-primary"><i class="bx bx-magic-wand bx-md"></i></span>
          </span>
          <h5>No documents yet</h5>
          <p class="text-muted">Generate a CV and a motivation letter tailored to this offer, written only from your profile.</p>
          <button class="btn btn-primary" type="button" (click)="generate()">Generate CV &amp; letter</button>
          <p class="small text-muted mt-3 mb-0">Profile not ready? <a routerLink="/profile">Complete it first</a>.</p>
        </div>
      </div>
    }
  `,
})
export class EditorPage {
  private readonly api = inject(ApplicationsService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute).snapshot;
  private readonly id = this.route.paramMap.get('id')!;
  /** Opened from "Create application & generate": start right away. */
  private readonly autoStart = this.route.queryParamMap.get('generate') === '1';
  private poll?: Subscription;

  readonly steps = STEPS;
  readonly app = signal<Application | null>(null);
  readonly job = signal<GenerationJob | null>(null);
  readonly cvDoc = signal<GeneratedDocument<CvContent> | null>(null);
  readonly letterDoc = signal<GeneratedDocument<LetterContent> | null>(null);
  readonly cv = signal<CvContent | null>(null);
  readonly letter = signal<LetterContent | null>(null);
  readonly dirty = signal(false);
  readonly saving = signal(false);
  readonly busySection = signal<string | null>(null);
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);
  readonly tab = signal<Tab>('cv');
  readonly preview = signal<'live' | 'pdf'>('live');

  readonly generating = computed(() => {
    const s = this.job()?.status;
    return s === 'QUEUED' || s === 'RUNNING';
  });
  readonly progress = computed(() => progressOf(this.job()));
  readonly stepIndex = computed(() => STEPS.findIndex((s) => s.key === this.job()?.step));
  readonly stepLabel = computed(() => STEPS[this.stepIndex()]?.label ?? 'Starting…');
  readonly reviewCount = computed(() => (this.cv()?.review.length ?? 0) + (this.letter()?.review.length ?? 0));

  constructor() {
    this.api.get(this.id).subscribe({ next: (a) => this.app.set(a), error: (e) => this.error.set(errorMessage(e)) });
    this.api.latestJob(this.id).subscribe({
      next: (job) => {
        this.job.set(job);
        this.loaded.set(true);
        if (job && (job.status === 'QUEUED' || job.status === 'RUNNING')) this.watch(job.id);
        else if (job) this.loadDocuments(job);
        else if (this.autoStart) this.generate();
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  generate(): void {
    this.error.set(null);
    this.api.generate(this.id).subscribe({
      next: (job) => {
        this.job.set(job);
        this.watch(job.id);
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  editCv(content: CvContent): void {
    this.cv.set(content);
    this.dirty.set(true);
  }

  editLetter(content: LetterContent): void {
    this.letter.set(content);
    this.dirty.set(true);
  }

  save(): void {
    const cvDoc = this.cvDoc();
    const letterDoc = this.letterDoc();
    if (!cvDoc || !letterDoc) return;
    this.saving.set(true);
    this.api.save(cvDoc.id, this.cv()!).pipe(
      switchMap((savedCv) => {
        this.cvDoc.set(savedCv);
        this.cv.set(savedCv.content);
        return this.api.save(letterDoc.id, this.letter()!);
      }),
    ).subscribe({
      next: (savedLetter) => {
        this.letterDoc.set(savedLetter);
        this.letter.set(savedLetter.content);
        this.dirty.set(false);
        this.saving.set(false);
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(errorMessage(e, 'Saving failed.'));
      },
    });
  }

  regenerate(kind: 'cv' | 'letter', section: string): void {
    const doc = kind === 'cv' ? this.cvDoc() : this.letterDoc();
    if (!doc) return;
    if (this.dirty() && !confirm('Regenerating reloads this document and discards your unsaved edits. Continue?')) return;
    this.busySection.set(section);
    this.error.set(null);
    this.api.regenerate<CvContent & LetterContent>(doc.id, section).subscribe({
      next: (updated) => {
        if (kind === 'cv') {
          this.cvDoc.set(updated as GeneratedDocument<CvContent>);
          this.cv.set(updated.content);
        } else {
          this.letterDoc.set(updated as GeneratedDocument<LetterContent>);
          this.letter.set(updated.content);
        }
        this.dirty.set(false);
        this.busySection.set(null);
      },
      error: (e) => {
        this.busySection.set(null);
        this.error.set(errorMessage(e, 'Regeneration failed.'));
      },
    });
  }

  /** A render updates the document's template/options/ATS fields; its content is unchanged. */
  onRendered(doc: GeneratedDocument<CvContent | LetterContent>): void {
    if (doc.type === 'CV') this.cvDoc.set(doc as GeneratedDocument<CvContent>);
    else this.letterDoc.set(doc as GeneratedDocument<LetterContent>);
  }

  pdfName(kind: string): string {
    const a = this.app();
    return `${kind} - ${a?.company ?? 'JobPilot'}.pdf`.replace(/[^\p{L}\p{N} ._-]/gu, '');
  }

  short(text: string): string {
    return text.length > 40 ? text.slice(0, 40) + '…' : text;
  }

  /** Polls the job every 3 s until it finishes, then loads the new documents. */
  private watch(jobId: string): void {
    this.poll?.unsubscribe();
    this.poll = timer(POLL_MS, POLL_MS)
      .pipe(
        switchMap(() => this.api.job(jobId)),
        filter((job) => {
          this.job.set(job);
          return job.status === 'DONE' || job.status === 'FAILED';
        }),
        take(1),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (job) => this.loadDocuments(job),
        error: (e) => this.error.set(errorMessage(e, 'Lost contact with the server while generating.')),
      });
  }

  private loadDocuments(job: GenerationJob): void {
    // A failed job keeps the previous documents (if any) visible.
    const source = job.status === 'DONE' ? job : null;
    const cvId = source?.cvDocumentId;
    const letterId = source?.letterDocumentId;
    if (!cvId || !letterId) {
      this.loadLatestFromList();
      return;
    }
    this.api.document<CvContent>(cvId).subscribe((d) => {
      this.cvDoc.set(d);
      this.cv.set(d.content);
      this.dirty.set(false);
    });
    this.api.document<LetterContent>(letterId).subscribe((d) => {
      this.letterDoc.set(d);
      this.letter.set(d.content);
    });
  }

  private loadLatestFromList(): void {
    fetchLatest(this.api, this.id, (cvDoc, letterDoc) => {
      if (cvDoc) {
        this.cvDoc.set(cvDoc);
        this.cv.set(cvDoc.content);
      }
      if (letterDoc) {
        this.letterDoc.set(letterDoc);
        this.letter.set(letterDoc.content);
      }
    });
  }
}

/** Loads the newest CV and letter of an application (used when the last job failed). */
function fetchLatest(
  api: ApplicationsService,
  applicationId: string,
  done: (cv: GeneratedDocument<CvContent> | null, letter: GeneratedDocument<LetterContent> | null) => void,
): void {
  api.documents(applicationId).subscribe((docs) => {
    const cvId = docs.find((d) => d.type === 'CV')?.id;
    const letterId = docs.find((d) => d.type === 'COVER_LETTER')?.id;
    if (!cvId && !letterId) {
      done(null, null);
      return;
    }
    if (cvId) api.document<CvContent>(cvId).subscribe((cv) => done(cv, null));
    if (letterId) api.document<LetterContent>(letterId).subscribe((l) => done(null, l));
  });
}
