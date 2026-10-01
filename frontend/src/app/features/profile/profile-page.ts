import { HttpEventType } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';

import { errorMessage } from '../../core/http/api-error';
import { HeaderForm } from './header-form';
import { ItemForm } from './item-form';
import { ItemRequest, ProfileItem, ProfileUpdate, SECTIONS, SectionMeta, dateRange } from './profile.models';
import { ProfileService } from './profile.service';

type ImportState = { phase: 'idle' } | { phase: 'uploading'; percent: number } | { phase: 'analyzing' };

/** Editing target: a section's "new" form, an existing item, or the header. */
type Editing = { kind: 'header' } | { kind: 'new'; type: string } | { kind: 'item'; id: string } | null;

/**
 * Master profile page: CV import + editable sections. All profile/LLM text is rendered with
 * interpolation (escaped), never as HTML.
 */
@Component({
  selector: 'app-profile-page',
  imports: [HeaderForm, ItemForm],
  template: `
    <div class="page-title-row">
      <h4 class="fw-bold py-1"><span class="text-muted fw-light">Account /</span> Master profile</h4>
      <div class="d-flex gap-2">
        <input #fileInput type="file" class="d-none" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
               (change)="onFile(fileInput)" />
        <button class="btn btn-primary" type="button" [disabled]="importing()" (click)="fileInput.click()">
          <i class="bx bx-upload me-1"></i> Import CV
        </button>
      </div>
    </div>

    @switch (importState().phase) {
      @case ('uploading') {
        <div class="card mb-4"><div class="card-body">
          <p class="mb-2 fw-semibold">Uploading your CV…</p>
          <div class="progress">
            <div class="progress-bar" role="progressbar" [style.width.%]="uploadPercent()"
                 [attr.aria-valuenow]="uploadPercent()" aria-valuemin="0" aria-valuemax="100">{{ uploadPercent() }}%</div>
          </div>
        </div></div>
      }
      @case ('analyzing') {
        <div class="alert alert-primary d-flex align-items-center mb-4" role="status">
          <span class="spinner-border spinner-border-sm me-3" aria-hidden="true"></span>
          <div>
            <strong>Reading your CV with the local AI model…</strong>
            <div class="small">This usually takes 30 seconds to 2 minutes. Keep this page open.</div>
          </div>
        </div>
      }
    }
    @if (importError()) {
      <div class="alert alert-danger alert-dismissible mb-4" role="alert">
        {{ importError() }}
        <button type="button" class="btn-close" aria-label="Close" (click)="importError.set(null)"></button>
      </div>
    }
    @if (importDone()) {
      <div class="alert alert-success alert-dismissible mb-4" role="alert">
        CV imported. Review every section below — the AI can misread things, and this profile is the only
        source of truth for your generated CVs.
        <button type="button" class="btn-close" aria-label="Close" (click)="importDone.set(false)"></button>
      </div>
    }

    @if (loadError()) {
      <div class="alert alert-danger">{{ loadError() }}</div>
    }

    @if (profile(); as p) {
      @if (service.isEmpty() && editing() === null) {
        <div class="card mb-4">
          <div class="card-body text-center py-5">
            <span class="avatar avatar-xl mx-auto mb-3 d-block">
              <span class="avatar-initial rounded-circle bg-label-primary"><i class="bx bx-file bx-md"></i></span>
            </span>
            <h5>Start with your existing CV</h5>
            <p class="text-muted mb-4">
              Upload a PDF or DOCX (max 5 MB). The local AI turns it into structured sections you can edit.<br />
              Prefer typing? Fill in the sections below by hand.
            </p>
            <button class="btn btn-primary" type="button" [disabled]="importing()" (click)="fileInput.click()">
              <i class="bx bx-upload me-1"></i> Import CV
            </button>
          </div>
        </div>
      }

      <!-- Header card -->
      <div class="card mb-4">
        <div class="card-body">
          @if (editing()?.kind === 'header') {
            <app-header-form [profile]="p" [busy]="saving()" [error]="saveError()"
                             (saved)="saveHeader($event)" (cancelled)="stopEditing()" />
          } @else {
            <div class="d-flex align-items-start gap-4 flex-wrap flex-sm-nowrap">
              <div class="avatar avatar-xl flex-shrink-0">
                <span class="avatar-initial rounded bg-label-primary fs-3">{{ initials() }}</span>
              </div>
              <div class="flex-grow-1">
                <h5 class="mb-1">{{ p.fullName || 'Your name' }}</h5>
                <p class="mb-2" [class.text-muted]="!p.headline">{{ p.headline || 'Add a headline' }}</p>
                <div class="d-flex flex-wrap gap-3 small text-muted mb-2">
                  <span><i class="bx bx-envelope me-1"></i>{{ p.email }}</span>
                  @if (p.phone) { <span><i class="bx bx-phone me-1"></i>{{ p.phone }}</span> }
                  @if (p.location) { <span><i class="bx bx-map me-1"></i>{{ p.location }}</span> }
                </div>
                @if (p.links.length || p.languages.length) {
                  <div class="d-flex flex-wrap gap-2 mb-2">
                    @for (link of p.links; track link.url) {
                      <a class="badge bg-label-primary text-none" [href]="link.url" target="_blank" rel="noopener noreferrer">
                        <i class="bx bx-link-external me-1"></i>{{ link.label || link.url }}
                      </a>
                    }
                    @for (lang of p.languages; track lang.name) {
                      <span class="badge bg-label-info text-none">{{ lang.name }}@if (lang.level) { · {{ lang.level }} }</span>
                    }
                  </div>
                }
                @if (p.summary) {
                  <p class="mb-0 white-space-pre-line">{{ p.summary }}</p>
                }
              </div>
              <button class="btn btn-sm btn-outline-primary flex-shrink-0" type="button" (click)="edit({ kind: 'header' })">
                <i class="bx bx-edit-alt me-1"></i>Edit
              </button>
            </div>
          }
        </div>
      </div>

      <!-- Sections -->
      @for (section of sections; track section.type) {
        <div class="card mb-4">
          <div class="card-header d-flex justify-content-between align-items-center">
            <h5 class="mb-0 d-flex align-items-center">
              <i class="bx me-2 text-primary" [class]="section.icon"></i>{{ section.label }}
              <span class="badge rounded-pill bg-label-secondary ms-2">{{ service.itemsOf(section.type).length }}</span>
            </h5>
            <button class="btn btn-sm btn-outline-primary" type="button"
                    (click)="edit({ kind: 'new', type: section.type })">
              <i class="bx bx-plus me-1"></i>Add
            </button>
          </div>
          <div class="card-body">
            @if (isEditing({ kind: 'new', type: section.type })) {
              <div class="mb-3">
                <app-item-form [meta]="section" [busy]="saving()" [error]="saveError()"
                               (saved)="addItem($event)" (cancelled)="stopEditing()" />
              </div>
            }

            @for (item of service.itemsOf(section.type); track item.id; let first = $first, last = $last) {
              <div class="py-3" [class.border-top]="!first">
                @if (isEditing({ kind: 'item', id: item.id })) {
                  <app-item-form [meta]="section" [item]="item" [busy]="saving()" [error]="saveError()"
                                 (saved)="updateItem(item, $event)" (cancelled)="stopEditing()" />
                } @else {
                  <div class="d-flex justify-content-between align-items-start gap-3">
                    <div class="flex-grow-1">
                      <h6 class="mb-1">{{ item.title || '(untitled)' }}</h6>
                      @if (item.organization || item.startDate || item.endDate) {
                        <div class="small text-muted mb-1">
                          {{ item.organization }}
                          @if (item.organization && label(section, item)) { · }
                          {{ label(section, item) }}
                        </div>
                      }
                      @if (item.description) {
                        <p class="mb-1 small white-space-pre-line">{{ item.description }}</p>
                      }
                      @if (item.bullets.length) {
                        <ul class="mb-1 ps-3 small">
                          @for (b of item.bullets; track $index) { <li>{{ b }}</li> }
                        </ul>
                      }
                      @if (item.tags.length) {
                        <div class="d-flex flex-wrap gap-1 mt-1">
                          @for (t of item.tags; track t) { <span class="badge bg-label-primary text-none">{{ t }}</span> }
                        </div>
                      }
                    </div>
                    <div class="d-flex gap-1 flex-shrink-0">
                      <button class="btn btn-sm btn-icon btn-outline-secondary" type="button" title="Move up"
                              aria-label="Move up" [disabled]="first || saving()" (click)="move(item, -1)">
                        <i class="bx bx-chevron-up"></i>
                      </button>
                      <button class="btn btn-sm btn-icon btn-outline-secondary" type="button" title="Move down"
                              aria-label="Move down" [disabled]="last || saving()" (click)="move(item, 1)">
                        <i class="bx bx-chevron-down"></i>
                      </button>
                      <button class="btn btn-sm btn-icon btn-outline-primary" type="button" title="Edit"
                              aria-label="Edit" (click)="edit({ kind: 'item', id: item.id })">
                        <i class="bx bx-edit-alt"></i>
                      </button>
                      <button class="btn btn-sm btn-icon btn-outline-danger" type="button" title="Delete"
                              aria-label="Delete" [disabled]="saving()" (click)="remove(item)">
                        <i class="bx bx-trash"></i>
                      </button>
                    </div>
                  </div>
                }
              </div>
            } @empty {
              @if (!isEditing({ kind: 'new', type: section.type })) {
                <p class="text-muted small mb-0">Nothing here yet.</p>
              }
            }
          </div>
        </div>
      }
    } @else if (!loadError()) {
      <div class="text-center py-5"><span class="spinner-border text-primary" role="status"></span></div>
    }
  `,
})
export class ProfilePage {
  readonly service = inject(ProfileService);
  readonly sections = SECTIONS;
  readonly profile = this.service.profile;

  readonly loadError = signal<string | null>(null);
  readonly editing = signal<Editing>(null);
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);

  readonly importState = signal<ImportState>({ phase: 'idle' });
  readonly importing = computed(() => this.importState().phase !== 'idle');
  readonly uploadPercent = computed(() => {
    const s = this.importState();
    return s.phase === 'uploading' ? s.percent : 100;
  });
  readonly importError = signal<string | null>(null);
  readonly importDone = signal(false);

  readonly initials = computed(() => {
    const p = this.profile();
    const name = p?.fullName || p?.email || '';
    return name.split(/[\s@.]+/).filter(Boolean).slice(0, 2).map((s) => s[0]!.toUpperCase()).join('');
  });

  constructor() {
    this.service.load().subscribe({ error: (err) => this.loadError.set(errorMessage(err)) });
  }

  label(section: SectionMeta, item: ProfileItem): string {
    return dateRange(item.startDate, item.endDate, section.dates === 'single');
  }

  edit(target: Exclude<Editing, null>): void {
    this.saveError.set(null);
    this.editing.set(target);
  }

  isEditing(target: Exclude<Editing, null>): boolean {
    const e = this.editing();
    return JSON.stringify(e) === JSON.stringify(target);
  }

  stopEditing(): void {
    this.editing.set(null);
    this.saveError.set(null);
  }

  saveHeader(req: ProfileUpdate): void {
    this.run(this.service.updateHeader(req));
  }

  addItem(req: ItemRequest): void {
    this.run(this.service.addItem(req));
  }

  updateItem(item: ProfileItem, req: ItemRequest): void {
    this.run(this.service.updateItem(item.id, req));
  }

  move(item: ProfileItem, delta: -1 | 1): void {
    this.run(this.service.move(item, delta), false);
  }

  remove(item: ProfileItem): void {
    if (confirm(`Delete "${item.title || 'this item'}"?`)) {
      this.run(this.service.deleteItem(item.id), false);
    }
  }

  onFile(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = ''; // allow re-selecting the same file
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      this.importError.set('The file is larger than 5 MB.');
      return;
    }
    const p = this.profile();
    if (p && !this.service.isEmpty() && !confirm('Importing a CV replaces your current profile. Continue?')) {
      return;
    }
    this.importError.set(null);
    this.importDone.set(false);
    this.stopEditing();
    this.importState.set({ phase: 'uploading', percent: 0 });

    this.service.importCv(file).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress) {
          const percent = event.total ? Math.round((100 * event.loaded) / event.total) : 0;
          this.importState.set(percent >= 100 ? { phase: 'analyzing' } : { phase: 'uploading', percent });
        } else if (event.type === HttpEventType.Response) {
          this.importState.set({ phase: 'idle' });
          this.importDone.set(true);
        }
      },
      error: (err) => {
        this.importState.set({ phase: 'idle' });
        this.importError.set(errorMessage(err, 'The import failed. Please try again.'));
      },
    });
  }

  private run(obs: ReturnType<ProfileService['load']>, closeEditor = true): void {
    this.saving.set(true);
    this.saveError.set(null);
    obs.subscribe({
      next: () => {
        this.saving.set(false);
        if (closeEditor) this.editing.set(null);
      },
      error: (err) => {
        this.saving.set(false);
        this.saveError.set(errorMessage(err));
        if (!closeEditor) alert(errorMessage(err));
      },
    });
  }
}
