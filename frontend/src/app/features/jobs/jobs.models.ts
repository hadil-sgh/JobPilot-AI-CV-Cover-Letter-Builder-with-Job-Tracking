export interface JobAnalysis {
  title: string | null;
  company: string | null;
  language: 'en' | 'fr' | string;
  seniority: string | null;
  requirements: string[];
  niceToHave: string[];
  keywords: string[];
  responsibilities: string[];
  tone: string | null;
}

export interface Job {
  id: string;
  language: string;
  sourceUrl: string | null;
  createdAt: string;
  analysis: JobAnalysis;
  text: string;
  warnings: string[];
}

export interface JobSummary {
  id: string;
  title: string | null;
  company: string | null;
  language: string;
  createdAt: string;
}

export interface EvidenceHit {
  itemId: string | null;
  type: string;
  content: string;
  similarity: number; // raw cosine similarity (nomic: ~0.45–0.8)
  score: number; // hybrid: similarity + 0.35 × share of the requirement's key terms found
  matchedTerms: string[];
}

export interface RequirementEvidence {
  requirement: string;
  mustHave: boolean;
  evidence: EvidenceHit[];
}

export interface EvidenceReport {
  jobId: string;
  profileChunks: number;
  minSimilarity: number;
  requirements: RequirementEvidence[];
}

/** Sneat badge colour for the best evidence's hybrid score. */
// Angular template safe navigation (`a?.b`) yields null, not undefined, so accept both.
export function matchLevel(score: number | null | undefined): { label: string; css: string } {
  if (score == null) return { label: 'No evidence', css: 'bg-label-danger' };
  if (score >= 0.85) return { label: 'Strong', css: 'bg-label-success' };
  if (score >= 0.65) return { label: 'Good', css: 'bg-label-info' };
  return { label: 'Weak', css: 'bg-label-warning' };
}

/** Share of must-have requirements with at least one piece of evidence (rough preview; Phase 4 scores properly). */
export function coverage(report: EvidenceReport | null): number | null {
  const must = report?.requirements.filter((r) => r.mustHave) ?? [];
  if (!must.length) return null;
  return Math.round((100 * must.filter((r) => r.evidence.length > 0).length) / must.length);
}
