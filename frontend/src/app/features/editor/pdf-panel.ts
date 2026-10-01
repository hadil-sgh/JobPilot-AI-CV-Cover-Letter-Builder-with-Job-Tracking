import { Component, DestroyRef, computed, effect, inject, input, output, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { errorMessage } from '../../core/http/api-error';
import {
  CvContent,
  GeneratedDocument,
  LetterContent,
  OptionSpec,
  SECTION_LABELS,
  TemplateManifest,
  initialOptions,
} from '../applications/applications.models';
import { ApplicationsService } from '../applications/applications.service';

/**
 * Template picker + options form generated from the manifest (enum → select, color → picker,
 * bool → switch, list → ordering), Render PDF, ATS report, embedded PDF and download.
 * A new template folder appears here without any Angular change.
 */
@Component({
  selector: 'app-pdf-panel',
  template: `
    <div class="card mb-3">
      <div class="card-body">
        <div class="row g-3 align-items-end">
          <div class="col-sm-7">
            <label class="form-label" for="tpl">Template</label>
            <select id="tpl" class="form-select" [value]="templateId()" (change)="selectTemplate(value($event))">
              @for (t of templates(); track t.id) {
                <option [value]="t.id">{{ t.name }}@if (t.atsSafe) { · ATS-safe }</option>
              }
            </select>
          </div>
          <div class="col-sm-5 d-grid">
            <button class="btn btn-primary" type="button" [disabled]="rendering() || dirty() || !manifest()" (click)="render()"
                    [title]="dirty() ? 'Save your edits first' : ''">
              @if (rendering()) { <span class="spinner-border spinner-border-sm me-1"></span>Rendering… }
              @else { <i class="bx bxs-file-pdf me-1"></i>{{ doc().hasPdf ? 'Re-render PDF' : 'Render PDF' }} }
            </button>
          </div>
        </div>
        @if (manifest()?.description) { <div class="form-text">{{ manifest()?.description }}</div> }

        @if (manifest(); as m) {
          <div class="row g-3 mt-1">
            @for (entry of optionEntries(); track entry.key) {
              @switch (entry.spec.type) {
                @case ('enum') {
                  <div class="col-sm-4">
                    <label class="form-label" [for]="'opt-' + entry.key">{{ entry.spec.label || entry.key }}</label>
                    <select class="form-select form-select-sm" [id]="'opt-' + entry.key" [value]="options()[entry.key]"
                            (change)="setOption(entry.key, value($event))">
                      @for (v of entry.spec.values ?? []; track v) { <option [value]="v">{{ v }}</option> }
                    </select>
                  </div>
                }
                @case ('color') {
                  <div class="col-sm-4">
                    <label class="form-label" [for]="'opt-' + entry.key">{{ entry.spec.label || entry.key }}</label>
                    <input type="color" class="form-control form-control-color w-100" [id]="'opt-' + entry.key"
                           [value]="options()[entry.key]" (input)="setOption(entry.key, value($event))" />
                  </div>
                }
                @case ('bool') {
                  <div class="col-sm-4 d-flex align-items-end">
                    <div class="form-check form-switch">
                      <input class="form-check-input" type="checkbox" [id]="'opt-' + entry.key" [checked]="options()[entry.key] === true"
                             (change)="setOption(entry.key, checked($event))" />
                      <label class="form-check-label" [for]="'opt-' + entry.key">{{ entry.spec.label || entry.key }}</label>
                    </div>
                  </div>
                }
                @case ('list') {
                  @if (isCv()) {
                    <div class="col-12">
                      <label class="form-label d-block">{{ entry.spec.label || entry.key }}</label>
                      <div class="d-flex flex-wrap gap-1">
                        @for (s of listValue(entry.key); track s; let i = $index, first = $first, last = $last) {
                          <span class="badge bg-label-secondary text-none d-inline-flex align-items-center gap-1 py-2">
                            <button class="btn btn-xs p-0 border-0" type="button" [disabled]="first" [attr.aria-label]="'Move ' + label(s) + ' earlier'"
                                    (click)="moveInList(entry.key, i, -1)"><i class="bx bx-chevron-left"></i></button>
                            {{ label(s) }}
                            <button class="btn btn-xs p-0 border-0" type="button" [disabled]="last" [attr.aria-label]="'Move ' + label(s) + ' later'"
                                    (click)="moveInList(entry.key, i, 1)"><i class="bx bx-chevron-right"></i></button>
                          </span>
                        }
                      </div>
                    </div>
                  }
                }
              }
            }
          </div>
        }

        @if (error()) { <div class="alert alert-danger py-2 mt-3 mb-0" role="alert">{{ error() }}</div> }
        @if (dirty()) { <div class="alert alert-warning py-2 mt-3 mb-0 small">Save your edits before rendering the PDF.</div> }
      </div>
    </div>

    @if (doc().atsReport; as r) {
      <div class="card mb-3">
        <div class="card-body">
          <div class="d-flex align-items-center gap-3 mb-2">
            <span class="badge fs-6" [class.bg-success]="r.score >= 90" [class.bg-warning]="r.score >= 60 && r.score < 90"
                  [class.bg-danger]="r.score < 60">ATS {{ r.score }}/100</span>
            <small class="text-muted">{{ r.pageCount }} page{{ r.pageCount > 1 ? 's' : '' }} · checked by extracting the text like a tracking system would</small>
          </div>
          <ul class="list-unstyled small mb-0">
            @for (c of r.checks; track c.id) {
              <li><i class="bx me-1" [class.bx-check-circle]="c.passed" [class.text-success]="c.passed"
                     [class.bx-x-circle]="!c.passed" [class.text-danger]="!c.passed"></i>
                <strong>{{ c.label }}</strong> <span class="text-muted">— {{ c.detail }}</span></li>
            }
          </ul>
        </div>
      </div>
    }

    @if (pdfUrl(); as url) {
      <div class="d-flex justify-content-end mb-2">
        <a class="btn btn-sm btn-outline-primary" [href]="downloadUrl()" [attr.download]="fileName()">
          <i class="bx bx-download me-1"></i>Download PDF
        </a>
      </div>
      <iframe class="w-100 border rounded" style="height: 78vh" [src]="url" title="PDF preview"></iframe>
    } @else if (!doc().hasPdf) {
      <div class="text-center text-muted py-5 border rounded">
        <i class="bx bxs-file-pdf bx-lg d-block mb-2"></i>Choose a template and render the PDF.
      </div>
    }
  `,
})
export class PdfPanel {
  private readonly api = inject(ApplicationsService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly doc = input.required<GeneratedDocument<CvContent | LetterContent>>();
  /** Unsaved edits in the editor: the PDF would not reflect them. */
  readonly dirty = input(false);
  readonly fileName = input('document.pdf');
  readonly rendered = output<GeneratedDocument<CvContent | LetterContent>>();

  readonly templates = signal<TemplateManifest[]>([]);
  readonly templateId = signal('ats-classic');
  readonly options = signal<Record<string, unknown>>({});
  readonly rendering = signal(false);
  readonly error = signal<string | null>(null);
  readonly pdfUrl = signal<SafeResourceUrl | null>(null);
  readonly downloadUrl = signal<string | null>(null);

  readonly manifest = computed(() => this.templates().find((t) => t.id === this.templateId()) ?? null);
  readonly optionEntries = computed(() =>
    Object.entries(this.manifest()?.options ?? {}).map(([key, spec]) => ({ key, spec: spec as OptionSpec })),
  );
  readonly isCv = computed(() => this.doc().type === 'CV');

  private objectUrl: string | null = null;
  private loadedFor: string | null = null;

  constructor() {
    this.api.templates().subscribe({
      next: (list) => {
        this.templates.set(list);
        this.syncFromDoc();
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
    // Reload the PDF whenever the shown document (or its rendered state) changes.
    effect(() => {
      const d = this.doc();
      this.syncFromDoc();
      const key = d.id + ':' + d.hasPdf + ':' + d.atsScore + ':' + (d.templateOptions ? JSON.stringify(d.templateOptions) : '');
      if (key !== this.loadedFor) {
        this.loadedFor = key;
        this.loadPdf(d);
      }
    });
    inject(DestroyRef).onDestroy(() => this.revoke());
  }

  value(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  checked(event: Event): boolean {
    return (event.target as HTMLInputElement).checked;
  }

  label(section: string): string {
    return SECTION_LABELS[section] ?? section;
  }

  listValue(key: string): string[] {
    const v = this.options()[key];
    return Array.isArray(v) ? (v as string[]) : [];
  }

  selectTemplate(id: string): void {
    this.templateId.set(id);
    const m = this.manifest();
    if (m) this.options.set(initialOptions(m, null));
  }

  setOption(key: string, value: unknown): void {
    this.options.set({ ...this.options(), [key]: value });
  }

  moveInList(key: string, index: number, delta: -1 | 1): void {
    const list = [...this.listValue(key)];
    [list[index], list[index + delta]] = [list[index + delta], list[index]];
    this.setOption(key, list);
  }

  render(): void {
    this.rendering.set(true);
    this.error.set(null);
    this.api.render(this.doc().id, this.templateId(), this.options()).subscribe({
      next: (doc) => {
        this.rendering.set(false);
        this.rendered.emit(doc);
      },
      error: (e) => {
        this.rendering.set(false);
        this.error.set(errorMessage(e, 'Rendering failed.'));
      },
    });
  }

  private syncFromDoc(): void {
    const d = this.doc();
    const id = d.template ?? this.templateId();
    const m = this.templates().find((t) => t.id === id);
    if (m) {
      this.templateId.set(m.id);
      this.options.set(initialOptions(m, d.templateOptions));
    }
  }

  private loadPdf(d: GeneratedDocument<CvContent | LetterContent>): void {
    this.revoke();
    if (!d.hasPdf) return;
    this.api.pdf(d.id).subscribe({
      next: (blob) => {
        this.objectUrl = URL.createObjectURL(blob);
        // Our own blob: URL for a PDF we just fetched — safe to embed.
        this.pdfUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.objectUrl));
        this.downloadUrl.set(this.objectUrl);
      },
      error: (e) => this.error.set(errorMessage(e, 'Could not load the PDF.')),
    });
  }

  private revoke(): void {
    if (this.objectUrl) URL.revokeObjectURL(this.objectUrl);
    this.objectUrl = null;
    this.pdfUrl.set(null);
    this.downloadUrl.set(null);
  }
}
