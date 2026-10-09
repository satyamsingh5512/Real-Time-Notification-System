import * as LabelPrimitive from '@radix-ui/react-label';
import type { ComponentPropsWithoutRef } from 'react';
import { cn } from '@/lib/utils';

/** Accessible form label. Every input in the app gets one — required by spec §33. */
export function Label({ className, ...props }: ComponentPropsWithoutRef<typeof LabelPrimitive.Root>) {
  return (
    <LabelPrimitive.Root
      className={cn(
        'text-xs font-medium leading-none text-foreground',
        'peer-disabled:cursor-not-allowed peer-disabled:opacity-70',
        className,
      )}
      {...props}
    />
  );
}