import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react';

/* Primitives implementing DESIGN.md component specs. */

type BtnVariant = 'primary' | 'secondary' | 'utility';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: BtnVariant;
  block?: boolean;
}

export function Button({ variant = 'primary', block = false, className = '', ...rest }: ButtonProps) {
  const cls = `nt-btn nt-btn-${variant}${block ? ' nt-btn-block' : ''} ${className}`.trim();
  return <button className={cls} {...rest} />;
}

export function Card({ children, elevated = false }: { children: ReactNode; elevated?: boolean }) {
  return <div className={`nt-card${elevated ? ' nt-card-elevated' : ''}`}>{children}</div>;
}

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
}

export function TextField({ label, ...rest }: FieldProps) {
  return (
    <div className="nt-field">
      <label>{label}</label>
      <input className="nt-input" {...rest} />
    </div>
  );
}

/** Category dot drawn from the decorative sticker palette (never structural). */
const STICKER: Record<string, string> = {
  INFO: 'var(--nt-sky)',
  SUCCESS: 'var(--nt-green)',
  WARNING: 'var(--nt-orange)',
  ERROR: 'var(--nt-pink)',
  EMAIL: 'var(--nt-sky)',
  SMS: 'var(--nt-orange)',
  PUSH: 'var(--nt-purple)',
  IN_APP: 'var(--nt-teal)',
  WEBSOCKET: 'var(--nt-teal)',
  LOW: 'var(--nt-green)',
  MEDIUM: 'var(--nt-sky)',
  HIGH: 'var(--nt-pink)',
};

export function Badge({ children, tone }: { children: ReactNode; tone?: string }) {
  const color = (tone && STICKER[tone]) || 'var(--nt-ink-faint)';
  return (
    <span className="nt-badge">
      <span className="nt-dot" style={{ background: color }} />
      {children}
    </span>
  );
}

export function EmptyState({ glyph, title, body }: { glyph: string; title: string; body: string }) {
  return (
    <div className="nt-empty">
      <div className="glyph">{glyph}</div>
      <div style={{ fontWeight: 700, color: 'var(--nt-ink)', marginBottom: 4 }}>{title}</div>
      <div>{body}</div>
    </div>
  );
}

export function StatCard({ label, value, tile }: { label: string; value: string; tile: string }) {
  return (
    <div className="nt-stat">
      <div className="tile" style={{ background: tile }} />
      <div className="lbl">{label}</div>
      <div className="val">{value}</div>
    </div>
  );
}

export function PageHeader({ eyebrow, title, sub }: { eyebrow: string; title: string; sub: string }) {
  return (
    <div className="nt-pagehead">
      <div className="nt-eyebrow">{eyebrow}</div>
      <h1 className="nt-h1">{title}</h1>
      <p className="nt-sub">{sub}</p>
    </div>
  );
}
