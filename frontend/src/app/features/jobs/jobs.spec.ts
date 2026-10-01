import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { EvidenceReport, coverage, matchLevel } from './jobs.models';
import { JobsService } from './jobs.service';

describe('jobs helpers', () => {
  it('maps similarity to a match level', () => {
    expect(matchLevel(undefined).label).toBe('No evidence');
    expect(matchLevel(null).label).toBe('No evidence');
    expect(matchLevel(1.09).label).toBe('Strong');
    expect(matchLevel(0.74).label).toBe('Good');
    expect(matchLevel(0.56).label).toBe('Weak');
  });

  it('computes must-have coverage and ignores nice-to-haves', () => {
    const hit = { itemId: null, type: 'SKILL', content: 'x', similarity: 0.7, score: 0.9, matchedTerms: ['x'] };
    const report: EvidenceReport = {
      jobId: 'j', profileChunks: 3, minSimilarity: 0.55,
      requirements: [
        { requirement: 'a', mustHave: true, evidence: [hit] },
        { requirement: 'b', mustHave: true, evidence: [] },
        { requirement: 'c', mustHave: false, evidence: [] },
      ],
    };
    expect(coverage(report)).toBe(50);
    expect(coverage(null)).toBeNull();
  });
});

describe('JobsService', () => {
  it('posts the pasted text and optional link', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);

    TestBed.inject(JobsService).analyze('some offer', '').subscribe();
    const req = http.expectOne('/api/jobs/analyze');
    expect(req.request.body).toEqual({ text: 'some offer', sourceUrl: null });
    req.flush({});
    http.verify();
  });
});
