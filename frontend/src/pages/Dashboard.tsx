import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { notificationsApi, type NotificationItem } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Badge, Button, Card, EmptyState, PageHeader, StatCard } from '../components/ui';

export function Dashboard({ unread, connected }: { unread: number; connected: boolean }) {
  const { user } = useAuth();
  const [recent, setRecent] = useState<NotificationItem[]>([]);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    notificationsApi
      .history(0, 5)
      .then(setRecent)
      .catch(() => setFailed(true));
  }, []);

  return (
    <>
      <PageHeader eyebrow="Workspace" title={`Good to see you, ${user?.displayName ?? 'there'}.`} sub="Your live notification overview — updates stream in without a refresh." />
      <div className="nt-stats">
        <StatCard label="Unread" value={String(unread)} tile="var(--nt-sky)" />
        <StatCard label="Connection" value={connected ? 'Live' : 'Offline'} tile={connected ? 'var(--nt-green)' : 'var(--nt-pink)'} />
        <StatCard label="Recent items" value={String(recent.length)} tile="var(--nt-purple)" />
        <StatCard label="Channels" value="5" tile="var(--nt-orange)" />
      </div>
      <Card elevated>
        <h3>Latest notifications</h3>
        {failed ? (
          <p className="nt-muted">Could not reach the backend. Start it with <code>docker compose up -d</code>.</p>
        ) : recent.length === 0 ? (
          <EmptyState glyph="✉" title="All caught up" body="New orders, mentions and alerts will land here in real time." />
        ) : (
          <div className="nt-table-wrap">
            <table className="nt-table">
              <thead>
                <tr>
                  <th>Subject</th>
                  <th>Channel</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {recent.map((n) => (
                  <tr key={n.id} className={n.read ? '' : 'unread'}>
                    <td>{n.subject ?? n.body ?? n.eventType}</td>
                    <td>
                      <Badge tone={n.channel}>{n.channel}</Badge>
                    </td>
                    <td>
                      <Badge tone={n.status === 'SENT' ? 'SUCCESS' : 'INFO'}>{n.status}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div style={{ marginTop: 16 }}>
          <Link to="/inbox">
            <Button variant="utility">Open notification center →</Button>
          </Link>
        </div>
      </Card>
    </>
  );
}
