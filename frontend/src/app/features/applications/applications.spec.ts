import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { GenerationJob, flagsFor, linesToBullets, progressOf } from './applications.models';
import { ApplicationsService } from './applications.service';

const job = (status: GenerationJob['status'], step: GenerationJob['step']): GenerationJob => ({
  id: 'j', applicationId: 'a', status, step, error: null, createdAt: '', startedAt: null, finishedAt: null,
  cvDocumentId: null, letterDocumentId: null,
});

describe('applications helpers', () => {
  it('maps job state to progress', () => {
    expect(progressOf(null)).toBe(0);
    expect(progressOf(job('QUEUED', null))).toBe(0);
    expect(progressOf(job('RUNNING', 'EVIDENCE'))).toBe(10);
    expect(progressOf(job('RUNNING', 'CV'))).toBe(50);
    expect(progressOf(job('DONE', null))).toBe(100);
  });

  it('filters review flags by section and parses bullets', () => {
    const flags = [{ section: 'summary', message: 'a' }, { section: 'experience:E1', message: 'b' }];
    expect(flagsFor(flags, 'experience:E1').map((f) => f.message)).toEqual(['b']);
    expect(flagsFor(null, 'summary')).toEqual([]);
    expect(linesToBullets('- one\n\n• two ')).toEqual(['one', 'two']);
  });
});

describe('ApplicationsService', () => {
  let api: ApplicationsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApplicationsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('returns null when an application was never generated (204)', () => {
    let result: GenerationJob | null | undefined;
    api.latestJob('a1').subscribe((j) => (result = j));
    http.expectOne('/api/applications/a1/generation').flush(null, { status: 204, statusText: 'No Content' });
    expect(result).toBeNull();
  });

  it('wraps edited content and posts section regeneration', () => {
    api.save('d1', { summary: 'x' } as never).subscribe();
    expect(http.expectOne('/api/documents/d1').request.body).toEqual({ content: { summary: 'x' } });

    api.regenerate('d1', 'experience:E1').subscribe();
    const req = http.expectOne('/api/documents/d1/regenerate-section');
    expect(req.request.body).toEqual({ section: 'experience:E1' });
    http.match(() => true).forEach((r) => r.flush({}));
  });

  it('posts a translation request', () => {
    api.translate('d1', 'fr').subscribe();
    const req = http.expectOne('/api/documents/d1/translate');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ language: 'fr' });
    req.flush({});
  });
});
