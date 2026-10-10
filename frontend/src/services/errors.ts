import { ApiError } from './api-client';

/**
 * Groups an `ApiError` into the UI-facing error state kind so pages can render the
 * right recovery affordance (retry / re-login / go back) without re-deriving the
 * status mapping in every component.
 */
export type ErrorKind = 'api' | 'auth' | 'forbidden' | 'notFound' | 'validation' | 'rateLimit' | 'server' | 'network' | 'unknown';

export interface NormalizedError {
  kind: ErrorKind;
  title: string;
  message: string;
  fieldErrors: Record<string, string>;
  retryable: boolean;
  /** Seconds to wait before retrying (429 only). */
  retryAfterSeconds: number | null;
}

const TITLES: Record<ErrorKind, string> = {
  api: 'Something went wrong on our side',
  auth: 'Your session has expired',
  forbidden: 'You do not have access to this',
  notFound: 'We could not find that',
  validation: 'Please check the details you entered',
  rateLimit: 'Too many requests',
  server: 'The service is temporarily unavailable',
  network: 'We could not reach the server',
  unknown: 'Unexpected error',
};

const RETRYABLE: Record<ErrorKind, boolean> = {
  api: true,
  auth: false,
  forbidden: false,
  notFound: false,
  validation: false,
  rateLimit: true,
  server: true,
  network: true,
  unknown: true,
};

export function normalizeError(error: unknown): NormalizedError {
  if (error instanceof ApiError) {
    const kindMap: Record<ApiError['kind'], ErrorKind> = {
      network: 'network',
      auth: 'auth',
      forbidden: 'forbidden',
      notFound: 'notFound',
      conflict: 'api',
      validation: 'validation',
      rateLimit: 'rateLimit',
      server: 'server',
      unavailable: 'server',
      unknown: 'unknown',
    };
    const kind = kindMap[error.kind];
    return {
      kind,
      title: TITLES[kind],
      message: error.message,
      fieldErrors: error.fieldErrors,
      retryable: RETRYABLE[kind],
      retryAfterSeconds: error.retryAfterSeconds,
    };
  }
  if (error instanceof Error) {
    return {
      kind: 'unknown',
      title: TITLES.unknown,
      // Never surface stack traces or raw internals to users (spec §15).
      message: 'An unexpected problem interrupted that request.',
      fieldErrors: {},
      retryable: true,
      retryAfterSeconds: null,
    };
  }
  return {
    kind: 'unknown',
    title: TITLES.unknown,
    message: 'An unexpected problem interrupted that request.',
    fieldErrors: {},
    retryable: true,
    retryAfterSeconds: null,
  };
}