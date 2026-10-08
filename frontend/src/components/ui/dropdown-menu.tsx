import * as DropdownMenuPrimitive from '@radix-ui/react-dropdown-menu';
import { Check, ChevronRight } from 'lucide-react';
import type { ComponentPropsWithoutRef } from 'react';
import { cn } from '@/lib/utils';

const DropdownMenu = DropdownMenuPrimitive.Root;
const DropdownMenuTrigger = DropdownMenuPrimitive.Trigger;
const DropdownMenuGroup = DropdownMenuPrimitive.Group;
const DropdownMenuPortal = DropdownMenuPrimitive.Portal;

const DropdownMenuContent = ({
  className,
  sideOffset = 6,
  ...props
}: ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.Content>) => (
  <DropdownMenuPrimitive.Portal>
    <DropdownMenuPrimitive.Content
      sideOffset={sideOffset}
      className={cn(
        'z-50 min-w-[11rem] overflow-hidden rounded-lg border border-border bg-popover p-1 text-popover-foreground shadow-lg',
        'data-[state=open]:animate-in data-[state=closed]:animate-out',
        'data-[state=open]:fade-in-0 data-[state=closed]:fade-out-0',
        'data-[state=open]:zoom-in-95 data-[state=closed]:zoom-out-95',
        className,
      )}
      {...props}
    />
  </DropdownMenuPrimitive.Portal>
);

const DropdownMenuItem = ({
  className,
  inset,
  variant = 'default',
  ...props
}: ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.Item> & {
  inset?: boolean;
  variant?: 'default' | 'destructive';
}) => (
  <DropdownMenuPrimitive.Item
    className={cn(
      'relative flex cursor-pointer select-none items-center gap-2 rounded-md px-2.5 py-2 text-sm outline-none transition-colors',
      'focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
      '[&_svg]:size-4 [&_svg]:shrink-0 [&_svg]:text-muted-foreground',
      inset && 'pl-8',
      variant === 'destructive' && 'text-destructive focus:bg-destructive/10 focus:text-destructive [&_svg]:text-destructive',
      className,
    )}
    {...props}
  />
);

const DropdownMenuCheckboxItem = ({
  className,
  children,
  ...props
}: ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.CheckboxItem>) => (
  <DropdownMenuPrimitive.CheckboxItem
    className={cn(
      'relative flex cursor-pointer select-none items-center gap-2 rounded-md py-2 pl-8 pr-2 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
      className,
    )}
    {...props}
  >
    <span className="absolute left-2 flex size-4 items-center justify-center">
      <DropdownMenuPrimitive.ItemIndicator>
        <Check className="size-4" aria-hidden="true" />
      </DropdownMenuPrimitive.ItemIndicator>
    </span>
    {children}
  </DropdownMenuPrimitive.CheckboxItem>
);

const DropdownMenuLabel = ({
  className,
  inset,
  ...props
}: ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.Label> & { inset?: boolean }) => (
  <DropdownMenuPrimitive.Label
    className={cn('px-2.5 py-1.5 text-xs font-medium text-muted-foreground', inset && 'pl-8', className)}
    {...props}
  />
);

const DropdownMenuSeparator = ({
  className,
  ...props
}: ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.Separator>) => (
  <DropdownMenuPrimitive.Separator className={cn('-mx-1 my-1 h-px bg-border', className)} {...props} />
);

const DropdownMenuSub = DropdownMenuPrimitive.Sub;
const DropdownMenuSubTrigger = ({
  className,
  children,
  ...props
}: ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.SubTrigger>) => (
  <DropdownMenuPrimitive.SubTrigger
    className={cn(
      'flex cursor-pointer select-none items-center gap-2 rounded-md px-2.5 py-2 text-sm outline-none focus:bg-accent data-[state=open]:bg-accent',
      className,
    )}
    {...props}
  >
    {children}
    <ChevronRight className="ml-auto size-4" aria-hidden="true" />
  </DropdownMenuPrimitive.SubTrigger>
);

const DropdownMenuSubContent = ({
  className,
  ...props
}: ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.SubContent>) => (
  <DropdownMenuPrimitive.SubContent
    className={cn(
      'z-50 min-w-[8rem] overflow-hidden rounded-lg border border-border bg-popover p-1 shadow-lg',
      className,
    )}
    {...props}
  />
);

export {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuCheckboxItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuGroup,
  DropdownMenuPortal,
  DropdownMenuSub,
  DropdownMenuSubTrigger,
  DropdownMenuSubContent,
};