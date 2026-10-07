import { ProfileLanguage, ProfileLink } from '../profile/profile.models';

export interface Application {
  id: string;
  jobId: string | null;
  company: string;
  roleTitle: string | null;
  country: string | null;
  workMode: 'REMOTE' | 'HYBRID' | 'ONSITE' | null;
  status: string;
  language: string | null;
  createdAt: string;
  updatedAt: string;
}

export type JobStatus = 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED';

export interface GenerationJob {
  id: string;
  applicationId: string;
  status: JobStatus;
  step: 'EVIDENCE' | 'MATCH' | 'CV' | 'LETTER' | 'SAVING' | null;
  error: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  cvDocumentId: string | null;
  letterDocumentId: string | null;
}

export interface ReviewFlag {
  section: string;
  message: string;
}

export interface CvEntry {
  ref: string;
  itemId: string;
  title: string | null;
  organization: string | null;
  start: string | null;
  end: string | null;
  description: string | null;
  bullets: string[];
  tags: string[];
}

export interface SkillGroup {
  group: string;
  items: string[];
}

export interface RequirementMatch {
  requirement: string;
  mustHave: boolean;
  verdict: 'yes' | 'partial' | 'no';
  evidenceScore: number;
  refs: string[];
}

export interface CvContent {
  language: string;
  header: {
    fullName: string | null;
    headline: string | null;
    email: string | null;
    phone: string | null;
    location: string | null;
    links: ProfileLink[];
  };
  summary: string | null;
  experience: CvEntry[];
  education: CvEntry[];
  projects: CvEntry[];
  skills: SkillGroup[];
  languages: ProfileLanguage[];
  certifications: CvEntry[];
  match: { score: number; gaps: string[]; requirements: RequirementMatch[] } | null;
  review: ReviewFlag[];
}

export interface LetterContent {
  language: string;
  company: string;
  roleTitle: string | null;
  greeting: string | null;
  paragraphs: string[];
  closing: string | null;
  signature: string | null;
  review: ReviewFlag[];
}

export interface AtsCheck {
  id: string;
  label: string;
  passed: boolean;
  detail: string;
  weight: number;
}

export interface AtsReport {
  score: number;
  pageCount: number;
  checks: AtsCheck[];
  extractedPreview: string;
}

export interface GeneratedDocument<T = CvContent | LetterContent> {
  id: string;
  applicationId: string;
  type: 'CV' | 'COVER_LETTER';
  version: number;
  language: string;
  template: string | null;
  templateVersion: string | null;
  templateOptions: Record<string, unknown> | null;
  matchScore: number | null;
  atsScore: number | null;
  atsReport: AtsReport | null;
  hasPdf: boolean;
  createdAt: string;
  content: T | null;
}

/** templates/latex/<id>/manifest.json as served by GET /api/templates. */
export interface OptionSpec {
  type: 'enum' | 'color' | 'bool' | 'list';
  label: string | null;
  values: string[] | null;
  default: unknown;
}

export interface TemplateManifest {
  id: string;
  name: string;
  version: string;
  description: string | null;
  atsSafe: boolean;
  engine: string;
  maxPages: number;
  languages: string[];
  sections: string[];
  options: Record<string, OptionSpec>;
}

/** Initial option values: what the document was last rendered with, else the manifest defaults. */
export function initialOptions(manifest: TemplateManifest, saved: Record<string, unknown> | null): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, spec] of Object.entries(manifest.options ?? {})) {
    const value = saved?.[key];
    out[key] = value !== undefined && value !== null ? value : spec.default;
  }
  return out;
}

export const SECTION_LABELS: Record<string, string> = {
  summary: 'Summary', experience: 'Experience', projects: 'Projects', education: 'Education',
  skills: 'Skills', certifications: 'Certifications', languages: 'Languages',
};

export const STEPS: { key: NonNullable<GenerationJob['step']>; label: string }[] = [
  { key: 'EVIDENCE', label: 'Collecting evidence from your profile' },
  { key: 'MATCH', label: 'Scoring your match' },
  { key: 'CV', label: 'Writing and fact-checking your CV' },
  { key: 'LETTER', label: 'Writing and fact-checking your letter' },
  { key: 'SAVING', label: 'Saving' },
];

/** 0–100 progress for the bar; queued = 0, unknown step = 5. */
export function progressOf(job: GenerationJob | null): number {
  if (!job) return 0;
  if (job.status === 'DONE') return 100;
  if (job.status === 'QUEUED') return 0;
  const i = STEPS.findIndex((s) => s.key === job.step);
  return i < 0 ? 5 : Math.round(((i + 0.5) / STEPS.length) * 100);
}

/** Review flags of one section ("summary", "experience:E1", "letter"...). */
export function flagsFor(flags: ReviewFlag[] | null | undefined, section: string): ReviewFlag[] {
  return (flags ?? []).filter((f) => f.section === section);
}

export function linesToBullets(text: string): string[] {
  return text.split('\n').map((l) => l.replace(/^\s*[-•*]\s*/, '').trim()).filter(Boolean);
}
