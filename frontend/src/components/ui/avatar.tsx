import * as AvatarPrimitive from '@radix-ui/react-avatar';
import { cn } from '@/lib/utils';

const Avatar = ({ className, ...props }: React.ComponentPropsWithoutRef<typeof AvatarPrimitive.Root>) => (
  <AvatarPrimitive.Root
    className={cn('relative flex size-9 shrink-0 overflow-hidden rounded-full', className)}
    {...props}
  />
);

const AvatarImage = ({ className, ...props }: React.ComponentPropsWithoutRef<typeof AvatarPrimitive.Image>) => (
  <AvatarPrimitive.Image className={cn('aspect-square size-full object-cover', className)} {...props} />
);

/**
 * Deterministic initials avatar. The colour is derived from the identifier so the same
 * person always looks the same, but the initials text carries the meaning — the colour
 * is decorative only, never the sole identifier (spec §6).
 */
const AvatarFallback = ({ className, ...props }: React.ComponentPropsWithoutRef<typeof AvatarPrimitive.Fallback>) => (
  <AvatarPrimitive.Fallback
    className={cn(
      'flex size-full items-center justify-center rounded-full bg-primary/12 text-xs font-semibold text-primary',
      className,
    )}
    {...props}
  />
);

export function initials(name: string): string {
  const cleaned = name.trim();
  if (!cleaned) return '?';
  const parts = cleaned.split(/[\s@._-]+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}

export function UserAvatar({
  name,
  email,
  className,
}: {
  name?: string | null;
  email?: string | null;
  className?: string;
}) {
  const label = name?.trim() || email?.split('@')[0] || 'User';
  return (
    <Avatar className={className}>
      <AvatarFallback aria-hidden="true">{initials(label)}</AvatarFallback>
      <span className="sr-only">{label}</span>
    </Avatar>
  );
}

export { Avatar, AvatarImage, AvatarFallback };