import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { EvidenceReport, Job, JobSummary } from './jobs.models';

@Injectable({ providedIn: 'root' })
export class JobsService {
  private readonly http = inject(HttpClient);

  analyze(text: string, sourceUrl: string | null): Observable<Job> {
    return this.http.post<Job>('/api/jobs/analyze', { text, sourceUrl: sourceUrl || null });
  }

  get(id: string): Observable<Job> {
    return this.http.get<Job>(`/api/jobs/${encodeURIComponent(id)}`);
  }

  recent(): Observable<JobSummary[]> {
    return this.http.get<JobSummary[]>('/api/jobs');
  }

  evidence(id: string): Observable<EvidenceReport> {
    return this.http.get<EvidenceReport>(`/api/jobs/${encodeURIComponent(id)}/evidence`);
  }
}
