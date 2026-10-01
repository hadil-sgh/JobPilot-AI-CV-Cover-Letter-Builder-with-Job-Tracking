import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Profile, ProfileItem, commaToList, dateRange, fromMonth, linesToList, toMonth } from './profile.models';
import { ProfileService } from './profile.service';

describe('profile form helpers', () => {
  it('converts between API dates and month inputs', () => {
    expect(toMonth('2021-03-01')).toBe('2021-03');
    expect(toMonth(null)).toBe('');
    expect(fromMonth('2021-03')).toBe('2021-03-01');
    expect(fromMonth('')).toBeNull();
  });

  it('splits bullets by line and strips list markers', () => {
    expect(linesToList('- Built an API\n\n• Cut latency 35%\n  * Mentored  ')).toEqual([
      'Built an API',
      'Cut latency 35%',
      'Mentored',
    ]);
  });

  it('splits tags by comma and de-duplicates case-insensitively', () => {
    expect(commaToList('Java, Spring ,java,, SQL')).toEqual(['Java', 'Spring', 'SQL']);
  });

  it('formats date ranges with Present for open-ended items', () => {
    expect(dateRange('2021-03-01', null)).toBe('Mar 2021 – Present');
    expect(dateRange(null, null)).toBe('');
    expect(dateRange('2023-06-01', null, true)).toBe('Jun 2023');
  });
});

describe('ProfileService', () => {
  let service: ProfileService;
  let http: HttpTestingController;

  const item = (id: string, sortOrder: number): ProfileItem => ({
    id, type: 'EXPERIENCE', title: id, organization: null, startDate: null, endDate: null,
    description: null, bullets: [], tags: [], sortOrder,
  });
  const profile = (items: ProfileItem[]): Profile => ({
    id: 'p1', fullName: null, email: 'a@b.c', headline: null, summary: null, phone: null, location: null,
    links: [], languages: [], hasOriginalFile: false, updatedAt: '', items,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ProfileService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the profile into the signal and sorts items per section', () => {
    service.load().subscribe();
    http.expectOne('/api/profile').flush(profile([item('b', 1), item('a', 0)]));

    expect(service.itemsOf('EXPERIENCE').map((i) => i.id)).toEqual(['a', 'b']);
    expect(service.isEmpty()).toBeFalse();
  });

  it('moving an item sends the new order of its section', () => {
    service.load().subscribe();
    http.expectOne('/api/profile').flush(profile([item('a', 0), item('b', 1), item('c', 2)]));

    service.move(service.itemsOf('EXPERIENCE')[2], -1).subscribe();
    const req = http.expectOne('/api/profile/items/order');
    expect(req.request.body).toEqual({ type: 'EXPERIENCE', ids: ['a', 'c', 'b'] });
    req.flush(profile([]));
  });

  it('uploads the CV as multipart form data', () => {
    const file = new File(['%PDF'], 'cv.pdf', { type: 'application/pdf' });
    service.importCv(file).subscribe();

    const req = http.expectOne('/api/profile/import');
    expect(req.request.body instanceof FormData).toBeTrue();
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush(profile([item('x', 0)]));
    expect(service.profile()?.items.length).toBe(1);
  });
});
