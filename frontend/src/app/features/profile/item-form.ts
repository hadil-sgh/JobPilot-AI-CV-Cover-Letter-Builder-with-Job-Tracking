import { Component, OnInit, inject, input, output } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import {
  ItemRequest,
  ProfileItem,
  SectionMeta,
  commaToList,
  fromMonth,
  linesToList,
  toMonth,
} from './profile.models';

/** Inline add/edit form for one profile item; fields depend on the section. */
@Component({
  selector: 'app-item-form',
  imports: [ReactiveFormsModule],
  template: `
    <form class="border rounded p-3 bg-lighter" [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <div class="row g-3">
        <div [class]="meta().orgLabel ? 'col-md-6' : 'col-12'">
          <label class="form-label" [for]="id('title')">{{ meta().titleLabel }}</label>
          <input class="form-control" [id]="id('title')" formControlName="title" maxlength="255" />
        </div>
        @if (meta().orgLabel) {
          <div class="col-md-6">
            <label class="form-label" [for]="id('org')">{{ meta().orgLabel }}</label>
            <input class="form-control" [id]="id('org')" formControlName="organization" maxlength="255" />
          </div>
        }
        @if (meta().dates) {
          <div class="col-md-6">
            <label class="form-label" [for]="id('start')">{{ meta().dates === 'single' ? 'Date' : 'Start' }}</label>
            <input class="form-control" type="month" [id]="id('start')" formControlName="start" />
          </div>
          @if (meta().dates === 'range') {
            <div class="col-md-6">
              <label class="form-label" [for]="id('end')">End <small class="text-muted">(empty = present)</small></label>
              <input class="form-control" type="month" [id]="id('end')" formControlName="end" />
            </div>
          }
        }
        @if (meta().description) {
          <div class="col-12">
            <label class="form-label" [for]="id('desc')">Description</label>
            <textarea class="form-control" rows="2" [id]="id('desc')" formControlName="description" maxlength="5000"></textarea>
          </div>
        }
        @if (meta().bullets) {
          <div class="col-12">
            <label class="form-label" [for]="id('bullets')">Achievements <small class="text-muted">(one per line, max 15)</small></label>
            <textarea class="form-control" rows="4" [id]="id('bullets')" formControlName="bullets"></textarea>
          </div>
        }
        @if (meta().tagsLabel) {
          <div class="col-12">
            <label class="form-label" [for]="id('tags')">{{ meta().tagsLabel }} <small class="text-muted">(comma separated)</small></label>
            <input class="form-control" [id]="id('tags')" formControlName="tags" />
          </div>
        }
      </div>

      @if (error()) {
        <div class="alert alert-danger py-2 mt-3 mb-0" role="alert">{{ error() }}</div>
      }

      <div class="mt-3 d-flex gap-2">
        <button class="btn btn-primary btn-sm" type="submit" [disabled]="busy()">
          {{ item() ? 'Save changes' : 'Add' }}
        </button>
        <button class="btn btn-outline-secondary btn-sm" type="button" (click)="cancelled.emit()">Cancel</button>
      </div>
    </form>
  `,
})
export class ItemForm implements OnInit {
  readonly meta = input.required<SectionMeta>();
  readonly item = input<ProfileItem | null>(null);
  readonly busy = input(false);
  readonly error = input<string | null>(null);
  readonly saved = output<ItemRequest>();
  readonly cancelled = output<void>();

  readonly form = inject(FormBuilder).nonNullable.group({
    title: ['', Validators.maxLength(255)],
    organization: ['', Validators.maxLength(255)],
    start: [''],
    end: [''],
    description: ['', Validators.maxLength(5000)],
    bullets: [''],
    tags: [''],
  });

  ngOnInit(): void {
    const i = this.item();
    if (i) {
      this.form.setValue({
        title: i.title ?? '',
        organization: i.organization ?? '',
        start: toMonth(i.startDate),
        end: toMonth(i.endDate),
        description: i.description ?? '',
        bullets: i.bullets.join('\n'),
        tags: i.tags.join(', '),
      });
    }
  }

  id(field: string): string {
    return `${this.meta().type}-${this.item()?.id ?? 'new'}-${field}`;
  }

  submit(): void {
    const v = this.form.getRawValue();
    const meta = this.meta();
    this.saved.emit({
      type: meta.type,
      title: v.title.trim() || null,
      organization: meta.orgLabel ? v.organization.trim() || null : null,
      startDate: meta.dates ? fromMonth(v.start) : null,
      endDate: meta.dates === 'range' ? fromMonth(v.end) : null,
      description: meta.description ? v.description.trim() || null : null,
      bullets: meta.bullets ? linesToList(v.bullets).slice(0, 15) : [],
      tags: meta.tagsLabel ? commaToList(v.tags) : [],
    });
  }
}
