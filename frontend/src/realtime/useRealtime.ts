import { useCallback, useEffect, useRef, useState } from 'react';
import { notificationsApi, wsUrl } from '../api/client';

export interface Toast {
  id: number;
  title: string;
  body: string;
}

let toastId = 1;

/** Live bell state: native WebSocket push + unread counter + ex-toast stack. */
export function useRealtime(enabled: boolean) {
  const [unread, setUnread] = useState(0);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [connected, setConnected] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);

  const pushToast = useCallback((title: string, body: string) => {
    const id = toastId++;
    setToasts((t) => [...t.slice(-3), { id, title, body }]);
    window.setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 6000);
  }, []);

  const refreshUnread = useCallback(async () => {
    try {
      const { unreadCount } = await notificationsApi.unreadCount();
      setUnread(unreadCount);
    } catch {
      /* backend unreachable — bell keeps last value */
    }
  }, []);

  const dismissToast = useCallback((id: number) => {
    setToasts((t) => t.filter((x) => x.id !== id));
  }, []);

  useEffect(() => {
    if (!enabled) return;
    void refreshUnread();
    const ws = new WebSocket(wsUrl());
    wsRef.current = ws;
    ws.onopen = () => setConnected(true);
    ws.onclose = () => setConnected(false);
    ws.onerror = () => setConnected(false);
    ws.onmessage = (ev: MessageEvent<string>) => {
      try {
        const msg = JSON.parse(ev.data) as { subject?: string; body?: string; title?: string };
        pushToast(msg.subject ?? msg.title ?? 'New notification', msg.body ?? '');
      } catch {
        pushToast('New notification', ev.data);
      }
      void refreshUnread();
    };
    return () => {
      ws.close();
      wsRef.current = null;
    };
  }, [enabled, refreshUnread, pushToast]);

  return { unread, toasts, connected, refreshUnread, pushToast, dismissToast };
}
