import { HttpErrorResponse } from '@angular/common/http';

import { ApiError } from '../auth/auth.models';

/** Turns any HTTP failure into a short message safe to show (plain text, never HTML). */
export function errorMessage(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'Cannot reach the server. Is the backend running?';
    }
    const body = err.error as Partial<ApiError> | null;
    if (body?.fieldErrors && Object.keys(body.fieldErrors).length > 0) {
      return Object.entries(body.fieldErrors)
        .map(([field, msg]) => `${field}: ${msg}`)
        .join(' · ');
    }
    if (typeof body?.message === 'string' && body.message) {
      return body.message;
    }
  }
  return fallback;
}
