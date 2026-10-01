import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { Application, CvContent, GeneratedDocument, GenerationJob, LetterContent } from './applications.models';

@Injectable({ providedIn: 'root' })
export class ApplicationsService {
  private readonly http = inject(HttpClient);

  create(jobId: string): Observable<Application> {
    return this.http.post<Application>('/api/applications', { jobId });
  }

  get(id: string): Observable<Application> {
    return this.http.get<Application>(`/api/applications/${encodeURIComponent(id)}`);
  }

  recent(): Observable<Application[]> {
    return this.http.get<Application[]>('/api/applications');
  }

  generate(id: string): Observable<GenerationJob> {
    return this.http.post<GenerationJob>(`/api/applications/${encodeURIComponent(id)}/generate`, {});
  }

  job(jobId: string): Observable<GenerationJob> {
    return this.http.get<GenerationJob>(`/api/generation-jobs/${encodeURIComponent(jobId)}`);
  }

  /** Latest job of an application, or null (204) if it was never generated. */
  latestJob(id: string): Observable<GenerationJob | null> {
    return this.http
      .get<GenerationJob>(`/api/applications/${encodeURIComponent(id)}/generation`, { observe: 'response' })
      .pipe(map((r: HttpResponse<GenerationJob>) => (r.status === 204 ? null : r.body)));
  }

  /** All document versions of an application, newest first per type (without content). */
  documents(id: string): Observable<GeneratedDocument[]> {
    return this.http.get<GeneratedDocument[]>(`/api/applications/${encodeURIComponent(id)}/documents`);
  }

  document<T extends CvContent | LetterContent>(docId: string): Observable<GeneratedDocument<T>> {
    return this.http.get<GeneratedDocument<T>>(`/api/documents/${encodeURIComponent(docId)}`);
  }

  save<T extends CvContent | LetterContent>(docId: string, content: T): Observable<GeneratedDocument<T>> {
    return this.http.put<GeneratedDocument<T>>(`/api/documents/${encodeURIComponent(docId)}`, { content });
  }

  regenerate<T extends CvContent | LetterContent>(docId: string, section: string): Observable<GeneratedDocument<T>> {
    return this.http.post<GeneratedDocument<T>>(`/api/documents/${encodeURIComponent(docId)}/regenerate-section`, {
      section,
    });
  }
}
