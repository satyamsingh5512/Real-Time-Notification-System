import { useEffect } from 'react';

type Handler = (event: KeyboardEvent) => void;

/**
 * Binds a global keyboard shortcut (spec §17, §37).
 *
 * Registered on the window in the capture phase so the shortcut still works while
 * focus sits inside a Radix popover or the command palette's own input. Callers are
 * responsible for ignoring events originating in text fields unless the binding is
 * explicitly a modified chord.
 */
export function useHotkey(binding: string, handler: Handler, enabled = true): void {
  useEffect(() => {
    if (!enabled) return;

    const onKeyDown = (event: KeyboardEvent) => {
      // Build the chord from the event so Shift/Meta stay significant across layouts.
      const parts: string[] = [];
      if (event.ctrlKey) parts.push('mod');
      if (event.metaKey) parts.push('mod');
      if (event.altKey) parts.push('alt');
      if (event.shiftKey) parts.push('shift');

      const key = event.key.length === 1 ? event.key.toLowerCase() : event.key.toLowerCase();
      const chord = [...parts, key].join('+');

      if (chord !== binding) return;
      event.preventDefault();
      handler(event);
    };

    window.addEventListener('keydown', onKeyDown, { capture: true });
    return () => window.removeEventListener('keydown', onKeyDown, { capture: true });
  }, [binding, handler, enabled]);
}

/**
 * True when the event came from somewhere the user is typing. Used to keep single-key
 * shortcuts from firing mid-message.
 */
export function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  return (
    tag === 'INPUT' ||
    tag === 'TEXTAREA' ||
    tag === 'SELECT' ||
    target.isContentEditable
  );
}