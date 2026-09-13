import { useCallback, useEffect, useState } from 'react';
import { notificationsApi, type NotificationItem } from '../api/client';
import { Badge, Button, EmptyState, PageHeader } from '../components/ui';

export function NotificationCenter({ onChanged }: { onChanged: () => void }) {
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [filter, setFilter] = useState<'all' | 'unread'>('all');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setItems(await notificationsApi.history(0, 20));
    } catch {
      setError('Could not load notifications. Is the backend running?');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const mutate = async (fn: () => Promise<unknown>) => {
    await fn();
    await load();
    onChanged();
  };

  const visible = filter === 'unread' ? items.filter((n) => !n.read) : items;

  return (
    <>
      <PageHeader eyebrow="Inbox" title="Notification Center" sub="History, unread filter, and one-click read states." />
      <div className="nt-toolbar">
        <button className={`nt-btn nt-btn-${filter === 'all' ? 'primary' : 'utility'}`} onClick={() => setFilter('all')}>
          All
        </button>
        <button className={`nt-btn nt-btn-${filter === 'unread' ? 'primary' : 'utility'}`} onClick={() => setFilter('unread')}>
          Unread
        </button>
        <span style={{ flex: 1 }} />
        <Button variant="utility" onClick={() => void load()}>
          ↻ Refresh
        </Button>
      </div>

      {loading ? (
        <p className="nt-muted">Loading…</p>
      ) : error ? (
        <div className="nt-form-error">{error}</div>
      ) : visible.length === 0 ? (
        <EmptyState glyph="✓" title={filter === 'unread' ? 'Zero unread' : 'Nothing here yet'} body="Live notifications will appear here the moment they are delivered." />
      ) : (
        <div className="nt-table-wrap">
          <table className="nt-table">
            <thead>
              <tr>
                <th style={{ width: 34 }} />
                <th>Notification</th>
                <th>Channel</th>
                <th>Status</th>
                <th>Received</th>
                <th style={{ width: 170 }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((n) => (
                <tr key={n.id} className={n.read ? '' : 'unread'}>
                  <td>{n.read ? null : <span className="nt-row-unread-bar" style={{ display: 'inline-block', height: 28 }} />}</td>
                  <td>
                    <div style={{ fontWeight: n.read ? 400 : 600 }}>{n.subject ?? n.eventType}</div>
                    <div className="nt-muted" style={{ fontWeight: 400, fontSize: 14 }}>{n.body}</div>
                  </td>
                  <td>
                    <Badge tone={n.channel}>{n.channel}</Badge>
                  </td>
                  <td>
                    <Badge tone={n.status === 'SENT' ? 'SUCCESS' : n.status === 'DEAD_LETTERED' ? 'ERROR' : 'INFO'}>
                      {n.status}
                    </Badge>
                  </td>
                  <td className="nt-muted" style={{ whiteSpace: 'nowrap' }}>
                    {new Date(n.createdAt).toLocaleString()}
                  </td>
                  <td>
                    <div style={{ display: 'flex', gap: 6 }}>
                      {n.read ? (
                        <Button variant="utility" onClick={() => void mutate(() => notificationsApi.markUnread(n.id))}>
                          Unread
                        </Button>
                      ) : (
                        <Button variant="utility" onClick={() => void mutate(() => notificationsApi.markRead(n.id))}>
                          Read
                        </Button>
                      )}
                      <Button variant="utility" onClick={() => void mutate(() => notificationsApi.remove(n.id))}>
                        ✕
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
