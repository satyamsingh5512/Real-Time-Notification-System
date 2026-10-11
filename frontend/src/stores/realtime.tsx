import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { notificationsApi, realtimeUrl } from '@/services/api';
import { toast } from '@/components/ui/toaster';
import type { NotificationItem, RealtimeFrame } from '@/types/api';

const MAX_BACKOFF_MS = 30_000;

/** A notification that just arrived over the socket, kept for optimistic list insertion. */
export interface LiveNotification {
  id: string;
  eventType: string;
  subject: string;
  body: string;
  createdAt: string;
}

interface RealtimeContextValue {
  unread: number;
  connected: boolean;
  /**
   * Notifications received this session, newest first. Pages subscribe to prepend
   * these so the list updates live instead of claiming to be live and not being so.
   */
  live: LiveNotification[];
  refreshUnread: () => Promise<void>;
}

const RealtimeContext = createContext<RealtimeContextValue | null>(null);

/**
 * Realtime notification channel (spec §21).
 *
 * This is a **provider**, not a per-component hook. It used to be called from the
 * shell, the dashboard, the inbox and the drawer, which meant the dashboard opened
 * three sockets — three reconnect backoffs, three watchdogs and three unread-count
 * requests. One socket, one unread count, one source of truth.
 *
 * Lifecycle contract:
 *  - exponential-backoff reconnect (1s → 30s cap) with jitter
 *  - 25s socket watchdog: the server sends 30s pings, so a silently dead socket is
 *    detected client-side too (nginx/LB can drop a connection without a close frame)
 *  - full teardown on unmount — no leaked sockets, timers or listeners
 *  - pauses while the tab is hidden so a backgrounded tab isn't holding a socket
 *
 * Realtime is an *enhancement*: unread counts always come from the REST API, so a
 * dropped socket degrades the experience rather than breaking it.
 */
export function RealtimeProvider({
  enabled,
  children,
}: {
  enabled: boolean;
  children: ReactNode;
}) {
  const [unread, setUnread] = useState(0);
  const [connected, setConnected] = useState(false);
  const [live, setLive] = useState<LiveNotification[]>([]);

  // Guards against a late frame from a socket that has already been torn down.
  const socketRef = useRef<WebSocket | null>(null);

  const refreshUnread = useCallback(async () => {
    try {
      const { unreadCount } = await notificationsApi.unreadCount();
      setUnread(unreadCount);
    } catch {
      // Backend unreachable: keep the last known count rather than flashing zero.
    }
  }, []);

  useEffect(() => {
    if (!enabled) {
      setConnected(false);
      return;
    }

    let reconnectTimer: number | null = null;
    let watchdogTimer: number | null = null;
    let attempt = 0;
    let disposed = false;

    const clearTimers = () => {
      if (reconnectTimer !== null) {
        window.clearTimeout(reconnectTimer);
        reconnectTimer = null;
      }
      if (watchdogTimer !== null) {
        window.clearInterval(watchdogTimer);
        watchdogTimer = null;
      }
    };

    const scheduleReconnect = () => {
      if (disposed) return;
      if (reconnectTimer !== null) return;
      const delay = Math.min(1000 * 2 ** attempt, MAX_BACKOFF_MS);
      attempt += 1;
      reconnectTimer = window.setTimeout(() => {
        reconnectTimer = null;
        connect();
      }, delay + Math.random() * 400);
    };

    const handleFrame = (raw: string) => {
      let frame: RealtimeFrame;
      try {
        frame = JSON.parse(raw) as RealtimeFrame;
      } catch {
        return; // ignore non-JSON frames rather than spamming the user
      }

      switch (frame.type) {
        case 'connected':
          setConnected(true);
          return;
        case 'pong':
          return;
        case 'unread_count':
          setUnread(frame.count);
          return;
        case 'notification': {
          const { notification } = frame;
          // Normalise the wire field name (`notificationId`) to `id` once, at the
          // boundary, so consumers never have to know about the envelope shape.
          const incoming: LiveNotification = {
            id: notification.notificationId,
            eventType: notification.eventType,
            subject: notification.subject,
            body: notification.body,
            createdAt: notification.createdAt,
          };
          // De-duplicate: a burst can carry the same id twice if the Redis subscriber
          // re-publishes during a reconnect.
          setLive((current) =>
            current.some((item) => item.id === incoming.id)
              ? current
              : [incoming, ...current].slice(0, 50),
          );
          toast(incoming.subject || 'New notification', {
            description: incoming.body || undefined,
          });
          void refreshUnread();
          return;
        }
        default:
          return;
      }
    };

    const connect = () => {
      if (disposed) return;
      let socket: WebSocket;
      try {
        socket = new WebSocket(realtimeUrl());
      } catch {
        scheduleReconnect();
        return;
      }
      socketRef.current = socket;

      socket.onopen = () => {
        attempt = 0;
        setConnected(true);
        watchdogTimer = window.setInterval(() => {
          if (socketRef.current && socketRef.current.readyState !== WebSocket.OPEN) {
            try {
              socketRef.current.close();
            } catch {
              /* onclose handles the reconnect */
            }
          }
        }, 25_000);
      };

      const handleDrop = () => {
        if (disposed) return;
        setConnected(false);
        if (watchdogTimer !== null) {
          window.clearInterval(watchdogTimer);
          watchdogTimer = null;
        }
        scheduleReconnect();
      };

      socket.onclose = handleDrop;
      socket.onerror = () => {
        try {
          socket.close();
        } catch {
          /* onclose will reconnect */
        }
      };
      socket.onmessage = (event: MessageEvent<string>) => handleFrame(event.data);
    };

    void refreshUnread();
    connect();

    // A hidden tab has no use for a live socket — hold no connection while backgrounded.
    const onVisibility = () => {
      if (document.visibilityState === 'visible') {
        if (!socketRef.current || socketRef.current.readyState > WebSocket.OPEN) {
          attempt = 0;
          clearTimers();
          connect();
        }
        void refreshUnread();
      } else {
        setConnected(false);
        try {
          socketRef.current?.close();
        } catch {
          /* onclose handles the reconnect */
        }
      }
    };
    document.addEventListener('visibilitychange', onVisibility);

    return () => {
      disposed = true;
      clearTimers();
      document.removeEventListener('visibilitychange', onVisibility);
      const socket = socketRef.current;
      socketRef.current = null;
      if (socket) {
        // Detach handlers first so teardown doesn't schedule a reconnect.
        socket.onclose = null;
        socket.onerror = null;
        socket.onmessage = null;
        try {
          socket.close();
        } catch {
          /* already closing */
        }
      }
    };
  }, [enabled, refreshUnread]);

  const value = useMemo<RealtimeContextValue>(
    () => ({ unread, connected, live, refreshUnread }),
    [unread, connected, live, refreshUnread],
  );

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}

export function useRealtime(): RealtimeContextValue {
  const context = useContext(RealtimeContext);
  if (!context) throw new Error('useRealtime must be used within RealtimeProvider');
  return context;
}

/**
 * Maps a socket-pushed notification onto the REST row shape so it can be prepended to
 * an existing list. The socket payload is a 5-field subset (no channel/status/priority/
 * read), so those are filled with the values the inbox will reconcile to shortly after —
 * `channel`/`status` are refreshed by the follow-up history fetch, `priority` defaults
 * server-side to MEDIUM when the column is null.
 */
export function toInboxRow(item: LiveNotification): NotificationItem {
  return {
    id: item.id,
    eventType: item.eventType,
    channel: 'IN_APP',
    subject: item.subject,
    body: item.body,
    status: 'DELIVERED',
    priority: 'MEDIUM',
    read: false,
    createdAt: item.createdAt,
    sentAt: item.createdAt,
  };
}