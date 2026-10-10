/**
 * Small, dependency-free date formatter.
 * Avoids pulling in a date library for the handful of formats the UI needs, keeping the
 * bundle lean on the Oracle Free Tier deployment.
 */

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** "just now" / "4m ago" / "3h ago" / "2d ago" / absolute date beyond a week. */
export function formatDistanceToNow(date: Date, { addSuffix = true } = {}): string {
  const diff = Date.now() - date.getTime();

  if (Number.isNaN(diff)) return '—';

  if (diff < MINUTE) return addSuffix ? 'just now' : 'now';

  const suffix = addSuffix ? ' ago' : '';
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)}m${suffix}`;
  if (diff < DAY) return `${Math.floor(diff / HOUR)}h${suffix}`;
  if (diff < 7 * DAY) return `${Math.floor(diff / DAY)}d${suffix}`;

  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

/** Short, human grouping label used by the notification centre's date separators. */
export function dayLabel(date: Date): string {
  const today = new Date();
  const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
  const startOfDate = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  const diffDays = Math.round((startOfToday - startOfDate) / DAY);

  if (diffDays === 0) return 'Today';
  if (diffDays === 1) return 'Yesterday';
  if (diffDays < 7) return 'Earlier this week';
  return date.toLocaleDateString(undefined, { month: 'long', day: 'numeric' });
}

/** Byte size for future file surfaces; also used for storage meters. */
export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / 1024 ** index;
  return `${value.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}