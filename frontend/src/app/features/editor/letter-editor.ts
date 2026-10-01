import { Component, input, output } from '@angular/core';

import { LetterContent, flagsFor } from '../applications/applications.models';

/** Editable motivation letter: greeting, paragraphs, closing. The signature comes from the profile. */
@Component({
  selector: 'app-letter-editor',
  template: `
    @let l = letter();
    <div class="card mb-4">
      <div class="card-header d-flex justify-content-between align-items-center">
        <h5 class="mb-0">Motivation letter</h5>
        <button class="btn btn-sm btn-outline-primary" type="button" [disabled]="busy()" (click)="regenerate.emit('letter')">
          @if (busy()) { <span class="spinner-border spinner-border-sm me-1"></span>Rewriting… }
          @else { <i class="bx bx-refresh me-1"></i>Regenerate }
        </button>
      </div>
      <div class="card-body">
        <div class="mb-3">
          <label class="form-label" for="greeting">Greeting</label>
          <input id="greeting" class="form-control" [value]="l.greeting ?? ''" (input)="patch({ greeting: value($event) })" />
        </div>
        @for (p of l.paragraphs; track $index; let i = $index) {
          <div class="mb-3">
            <div class="d-flex justify-content-between">
              <label class="form-label" [for]="'para' + i">Paragraph {{ i + 1 }}</label>
              <button class="btn btn-sm btn-link text-danger p-0" type="button" (click)="removeParagraph(i)">Remove</button>
            </div>
            <textarea class="form-control" rows="4" [id]="'para' + i" [value]="p" (input)="setParagraph(i, value($event))"></textarea>
          </div>
        }
        <button class="btn btn-sm btn-outline-secondary mb-3" type="button" (click)="addParagraph()">
          <i class="bx bx-plus me-1"></i>Paragraph
        </button>
        <div class="mb-2">
          <label class="form-label" for="closing">Closing</label>
          <input id="closing" class="form-control" [value]="l.closing ?? ''" (input)="patch({ closing: value($event) })" />
        </div>
        <div class="form-text">Signed: {{ l.signature }} · {{ words() }} words (target 250–350)</div>

        @if (flags().length) {
          <div class="alert alert-warning py-2 mt-3 mb-0 small" role="alert">
            <i class="bx bx-error me-1"></i><strong>Please review:</strong>
            <ul class="mb-0 ps-3">@for (f of flags(); track $index) { <li>{{ f.message }}</li> }</ul>
          </div>
        }
      </div>
    </div>
  `,
})
export class LetterEditor {
  readonly letter = input.required<LetterContent>();
  readonly busy = input(false);
  readonly changed = output<LetterContent>();
  readonly regenerate = output<string>();

  flags() {
    return flagsFor(this.letter().review, 'letter');
  }

  words(): number {
    return this.letter().paragraphs.join(' ').split(/\s+/).filter(Boolean).length;
  }

  value(event: Event): string {
    return (event.target as HTMLInputElement | HTMLTextAreaElement).value;
  }

  patch(change: Partial<LetterContent>): void {
    this.changed.emit({ ...this.letter(), ...change });
  }

  setParagraph(index: number, text: string): void {
    this.patch({ paragraphs: this.letter().paragraphs.map((p, i) => (i === index ? text : p)) });
  }

  addParagraph(): void {
    this.patch({ paragraphs: [...this.letter().paragraphs, ''] });
  }

  removeParagraph(index: number): void {
    this.patch({ paragraphs: this.letter().paragraphs.filter((_, i) => i !== index) });
  }
}
