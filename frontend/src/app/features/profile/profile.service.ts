import { HttpClient, HttpEvent } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, switchMap, tap } from 'rxjs';

import { ItemRequest, ItemType, Profile, ProfileItem, ProfileUpdate } from './profile.models';

/** Profile state as a signal; every mutation refreshes it from the server (single source of truth). */
@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);

  private readonly _profile = signal<Profile | null>(null);
  readonly profile = this._profile.asReadonly();
  readonly isEmpty = computed(() => {
    const p = this._profile();
    return !!p && p.items.length === 0 && !p.headline && !p.summary;
  });

  itemsOf(type: ItemType): ProfileItem[] {
    return (this._profile()?.items ?? []).filter((i) => i.type === type).sort((a, b) => a.sortOrder - b.sortOrder);
  }

  load(): Observable<Profile> {
    return this.http.get<Profile>('/api/profile').pipe(tap((p) => this._profile.set(p)));
  }

  updateHeader(req: ProfileUpdate): Observable<Profile> {
    return this.http.put<Profile>('/api/profile', req).pipe(tap((p) => this._profile.set(p)));
  }

  addItem(req: ItemRequest): Observable<Profile> {
    return this.http.post<ProfileItem>('/api/profile/items', req).pipe(switchMap(() => this.load()));
  }

  updateItem(id: string, req: ItemRequest): Observable<Profile> {
    return this.http.put<ProfileItem>(`/api/profile/items/${id}`, req).pipe(switchMap(() => this.load()));
  }

  deleteItem(id: string): Observable<Profile> {
    return this.http.delete<void>(`/api/profile/items/${id}`).pipe(switchMap(() => this.load()));
  }

  /** Moves an item one step up (-1) or down (+1) within its section. */
  move(item: ProfileItem, delta: -1 | 1): Observable<Profile> {
    const ids = this.itemsOf(item.type).map((i) => i.id);
    const from = ids.indexOf(item.id);
    const to = from + delta;
    [ids[from], ids[to]] = [ids[to], ids[from]];
    return this.http
      .put<Profile>('/api/profile/items/order', { type: item.type, ids })
      .pipe(tap((p) => this._profile.set(p)));
  }

  /** Uploads a CV; emits progress events, the final response is the new profile. */
  importCv(file: File): Observable<HttpEvent<Profile>> {
    const body = new FormData();
    body.append('file', file);
    return this.http
      .post<Profile>('/api/profile/import', body, { reportProgress: true, observe: 'events' })
      .pipe(
        tap((event) => {
          if ('body' in event && event.body) this._profile.set(event.body);
        }),
      );
  }
}
