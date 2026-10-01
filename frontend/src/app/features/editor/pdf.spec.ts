import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TemplateManifest, initialOptions } from '../applications/applications.models';
import { ApplicationsService } from '../applications/applications.service';

const manifest: TemplateManifest = {
  id: 'ats-classic', name: 'ATS Classic', version: '1.0.0', description: null, atsSafe: true, engine: 'xetex',
  maxPages: 1, languages: ['en', 'fr'], sections: ['summary', 'experience'],
  options: {
    fontSize: { type: 'enum', label: 'Font size', values: ['10pt', '11pt'], default: '10pt' },
    accentColor: { type: 'color', label: 'Accent', values: null, default: '#1F3A5F' },
    sectionOrder: { type: 'list', label: 'Order', values: null, default: ['summary', 'experience'] },
  },
};

describe('template options', () => {
  it('uses saved values over manifest defaults', () => {
    expect(initialOptions(manifest, null)).toEqual({
      fontSize: '10pt', accentColor: '#1F3A5F', sectionOrder: ['summary', 'experience'],
    });
    expect(initialOptions(manifest, { fontSize: '11pt', unknown: 1 })).toEqual({
      fontSize: '11pt', accentColor: '#1F3A5F', sectionOrder: ['summary', 'experience'],
    });
  });

  it('posts render requests and fetches the PDF as a blob', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const api = TestBed.inject(ApplicationsService);
    const http = TestBed.inject(HttpTestingController);

    api.render('d1', 'ats-classic', { fontSize: '11pt' }).subscribe();
    expect(http.expectOne('/api/documents/d1/render').request.body).toEqual({
      template: 'ats-classic', options: { fontSize: '11pt' },
    });

    api.pdf('d1').subscribe();
    expect(http.expectOne('/api/documents/d1/pdf').request.responseType).toBe('blob');
    http.match(() => true).forEach((r) => r.flush(null));
  });
});
