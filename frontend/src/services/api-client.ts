/**
 * Typed API layer (spec §32). No mocks, no invented endpoints — every function here maps
 * to a controller in `api/src/main/java/com/uber/notification/api/controller`.
 *
 * Error taxonomy: 401 (session), 403 (RBAC), 404, 409, 422 (validation), 429 (rate limit),
 * 500/503 (server) are mapped to distinct `ApiErrorKind` values so the UI can offer the
 * right recovery action instead of a generic "something went wrong".
 */

export const TOKEN_STORAGE_KEY = 'rtns.token';
export const USER_STORAGE_KEY = 'rtns.user';

const API_URL = (import.meta.env.VITE_API_URL as string | undefined) ?? '';

export type ApiErrorKind =
  | 'network'
  | 'auth'
  | 'forbidden'
  | 'notFound'
  | 'conflict'
  | 'validation'
  | 'rateLimit'
  | 'server'
  | 'unavailable'
  | 'unknown';

export class ApiError extends Error {
  readonly status: number;
  readonly kind: ApiErrorKind;
  readonly retryAfterSeconds: number | null;
  /** Field-level validation messages, when the backend supplies them. */
  readonly fieldErrors: Record<string, string>;

  constructor(
    status: number,
    kind: ApiErrorKind,
    message: string,
    options: { retryAfterSeconds?: number | null; fieldErrors?: Record<string, string> } = {},
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.kind = kind;
    this.retryAfterSeconds = options.retryAfterSeconds ?? null;
    this.fieldErrors = options.fieldErrors ?? {};
  }

  /** Maps an HTTP status onto the taxonomy above (spec §32). */
  static fromStatus(status: number, message?: string, body?: unknown): ApiError {
    const kindMap: Record<number, ApiErrorKind> = {
      400: 'validation',
      401: 'auth',
      403: 'forbidden',
      404: 'notFound',
      409: 'conflict',
      422: 'validation',
      429: 'rateLimit',
      500: 'server',
      502: 'server',
      503: 'unavailable',
      504: 'network',
    };
    const kind = kindMap[status] ?? 'unknown';
    const fieldErrors = extractFieldErrors(body);
    return new ApiError(status, kind, message ?? defaultMessage(kind), { fieldErrors });
  }
}

function defaultMessage(kind: ApiErrorKind): string {
  switch (kind) {
    case 'auth':
      return 'Your session has expired. Please sign in again.';
    case 'forbidden':
      return 'Your role does not allow this action.';
    case 'notFound':
      return 'That resource no longer exists.';
    case 'conflict':
      return 'That change conflicts with the current state. Refresh and try again.';
    case 'validation':
      return 'Some of the details you entered are invalid.';
    case 'rateLimit':
      return 'Too many requests. Please wait a moment and retry.';
    case 'unavailable':
      return 'The service is temporarily unavailable.';
    case 'network':
      return 'We could not reach the server. Check your connection.';
    case 'server':
      return 'Something went wrong on our side.';
    default:
      return 'Unexpected error. Please try again.';
  }
}

/** Best-effort extraction of field errors from Spring's default error body. */
function extractFieldErrors(body: unknown): Record<string, string> {
  if (!body || typeof body !== 'object') return {};
  const record = body as Record<string, unknown>;
  const errors = record.errors ?? record.fieldErrors;
  if (Array.isArray(errors)) {
    const result: Record<string, string> = {};
    for (const entry of errors) {
      if (entry && typeof entry === 'object') {
        const item = entry as Record<string, unknown>;
        const field = typeof item.field === 'string' ? item.field : undefined;
        const message = typeof item.message === 'string' ? item.message : typeof item.defaultMessage === 'string' ? item.defaultMessage : null;
        if (field && message) result[field] = message;
      }
    }
    return result;
  }
  if (errors && typeof errors === 'object') {
    const result: Record<string, string> = {};
    for (const [key, value] of Object.entries(errors as Record<string, unknown>)) {
      if (typeof value === 'string') result[key] = value;
    }
    return result;
  }
  return {};
}

function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_STORAGE_KEY);
  } catch {
    return null;
  }
}

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  /** Set false for public endpoints so no Authorization header is attached. */
  authenticated?: boolean;
  signal?: AbortSignal;
}

type UnauthorizedHandler = () => void;

/**
 * Single place where a 401 clears the session, so the shell can redirect to /login
 * exactly once instead of every caller handling it (spec §32).
 */
let unauthorizedHandler: UnauthorizedHandler | null = null;
export function onUnauthorized(handler: UnauthorizedHandler | null) {
  unauthorizedHandler = handler;
}

export function clearSession() {
  try {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(USER_STORAGE_KEY);
  } catch {
    /* storage unavailable */
  }
}

async function parseBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined;
  const contentType = response.headers.get('content-type') ?? '';
  try {
    if (contentType.includes('application/json')) return await response.json();
    const text = await response.text();
    return text ? { message: text } : undefined;
  } catch {
    return undefined;
  }
}

function safeMessage(body: unknown, fallback: string): string {
  if (body && typeof body === 'object') {
    const message = (body as Record<string, unknown>).message ?? (body as Record<string, unknown>).error;
    if (typeof message === 'string' && message.trim()) return message;
  }
  return fallback;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, authenticated = true, signal, headers, ...rest } = options;

  const requestHeaders = new Headers(headers);
  if (body !== undefined && !requestHeaders.has('Content-Type')) {
    requestHeaders.set('Content-Type', 'application/json');
  }
  if (authenticated) {
    const token = readToken();
    if (token) requestHeaders.set('Authorization', `Bearer ${token}`);
  }

  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      ...rest,
      signal,
      headers: requestHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError(0, 'network', defaultMessage('network'));
  }

  if (response.status === 401) {
    clearSession();
    unauthorizedHandler?.();
    throw ApiError.fromStatus(401, safeMessage(await parseBody(response), defaultMessage('auth')));
  }

  if (response.status === 429) {
    const retryAfter = response.headers.get('Retry-After');
    const seconds = retryAfter ? Number.parseInt(retryAfter, 10) : null;
    throw new ApiError(429, 'rateLimit', defaultMessage('rateLimit'), {
      retryAfterSeconds: Number.isFinite(seconds) ? seconds : null,
    });
  }

  if (!response.ok) {
    const parsed = await parseBody(response);
    throw ApiError.fromStatus(response.status, safeMessage(parsed, defaultMessage('unknown')), parsed);
  }

  return (await parseBody(response)) as T;
}