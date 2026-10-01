export type ItemType = 'EXPERIENCE' | 'EDUCATION' | 'PROJECT' | 'SKILL' | 'CERTIFICATION';

export interface ProfileLink {
  label: string | null;
  url: string;
}

export interface ProfileLanguage {
  name: string;
  level: string | null;
}

export interface ProfileItem {
  id: string;
  type: ItemType;
  title: string | null;
  organization: string | null;
  startDate: string | null; // yyyy-MM-dd
  endDate: string | null;
  description: string | null;
  bullets: string[];
  tags: string[];
  sortOrder: number;
}

export interface Profile {
  id: string;
  fullName: string | null;
  email: string;
  headline: string | null;
  summary: string | null;
  phone: string | null;
  location: string | null;
  links: ProfileLink[];
  languages: ProfileLanguage[];
  hasOriginalFile: boolean;
  updatedAt: string;
  items: ProfileItem[];
}

export interface ProfileUpdate {
  fullName: string | null;
  headline: string | null;
  summary: string | null;
  phone: string | null;
  location: string | null;
  links: ProfileLink[];
  languages: ProfileLanguage[];
}

export type ItemRequest = Omit<ProfileItem, 'id' | 'sortOrder'>;

/** How each section is labelled and which fields its form shows. */
export interface SectionMeta {
  type: ItemType;
  label: string;
  icon: string;
  titleLabel: string;
  orgLabel?: string;
  dates?: 'range' | 'single';
  description?: boolean;
  bullets?: boolean;
  tagsLabel?: string;
}

export const SECTIONS: SectionMeta[] = [
  { type: 'EXPERIENCE', label: 'Experience', icon: 'bx-briefcase', titleLabel: 'Job title', orgLabel: 'Company',
    dates: 'range', description: true, bullets: true },
  { type: 'EDUCATION', label: 'Education', icon: 'bx-book-reader', titleLabel: 'Degree', orgLabel: 'School',
    dates: 'range', description: true },
  { type: 'PROJECT', label: 'Projects', icon: 'bx-code-block', titleLabel: 'Project name', description: true,
    bullets: true, tagsLabel: 'Technologies' },
  { type: 'SKILL', label: 'Skills', icon: 'bx-wrench', titleLabel: 'Group (e.g. Languages, Frameworks)',
    tagsLabel: 'Skills' },
  { type: 'CERTIFICATION', label: 'Certifications', icon: 'bx-award', titleLabel: 'Certification', orgLabel: 'Issuer',
    dates: 'single' },
];

// ---- form helpers (pure, unit-tested) ----

/** "2021-03-01" -> "2021-03" for <input type="month">. */
export function toMonth(date: string | null): string {
  return date ? date.slice(0, 7) : '';
}

/** "2021-03" -> "2021-03-01"; empty -> null. */
export function fromMonth(month: string): string | null {
  return /^\d{4}-\d{2}$/.test(month) ? `${month}-01` : null;
}

export function linesToList(text: string): string[] {
  return text.split('\n').map((l) => l.replace(/^\s*[-•*]\s*/, '').trim()).filter(Boolean);
}

export function commaToList(text: string): string[] {
  const seen = new Set<string>();
  return text
    .split(/[,\n]/)
    .map((t) => t.trim())
    .filter((t) => t && !seen.has(t.toLowerCase()) && seen.add(t.toLowerCase()));
}

/** "Mar 2021 – Present" style label for display. */
export function dateRange(start: string | null, end: string | null, single = false): string {
  const fmt = (d: string) =>
    new Date(`${d}T00:00:00`).toLocaleDateString('en', { month: 'short', year: 'numeric' });
  if (single) return start ? fmt(start) : '';
  if (!start && !end) return '';
  return `${start ? fmt(start) : '?'} – ${end ? fmt(end) : 'Present'}`;
}
