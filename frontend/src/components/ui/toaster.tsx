import { Toaster as Sonner, toast } from 'sonner';
import { useTheme } from '@/stores/theme';

/**
 * Unified toast system (spec §16).
 * - Auto-dismisses, stays readable, never covers the topbar controls (offset below it)
 * - Keyboard accessible (Sonner renders a live region and supports focus movement)
 * - Manual dismissal always available
 */
export function AppToaster() {
  const { theme } = useTheme();
  return (
    <Sonner
      theme={theme}
      position="bottom-right"
      offset={16}
      visibleToasts={4}
      closeButton
      richColors={false}
      toastOptions={{
        classNames: {
          toast:
            'group rounded-lg border border-border bg-card text-card-foreground shadow-lg text-sm',
          description: 'text-muted-foreground',
          actionButton: 'bg-primary text-primary-foreground',
          cancelButton: 'bg-muted text-muted-foreground',
          error: 'border-destructive/40',
          success: 'border-success/40',
        },
      }}
    />
  );
}

export { toast };