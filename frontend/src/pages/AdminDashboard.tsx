import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { adminApi, type AdminStats } from '../api/client';
import { Button, Card, EmptyState, PageHeader, StatCard, TextField } from '../components/ui';

export function AdminDashboard({ notify }: { notify: (title: string, body: string) => void }) {
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [statsError, setStatsError] = useState(false);
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');
  const [sending, setSending] = useState(false);

  useEffect(() => {
    adminApi
      .stats()
      .then(setStats)
      .catch(() => setStatsError(true));
  }, []);

  const broadcast = async (e: FormEvent) => {
    e.preventDefault();
    setSending(true);
    try {
      const res = await adminApi.broadcast(title, message);
      notify('Broadcast sent', `Delivered to ${res.delivered} user(s).`);
      setTitle('');
      setMessage('');
    } catch {
      notify('Broadcast failed', 'Only admins can broadcast; check your role.');
    } finally {
      setSending(false);
    }
  };

  return (
    <>
      <PageHeader eyebrow="Admin" title="Admin Dashboard" sub="Delivery analytics and broadcast controls." />
      {statsError || stats == null ? (
        <EmptyState
          glyph="⬣"
          title="Analytics unavailable"
          body="The /api/v1/admin/stats endpoint is not enabled on this backend build yet — broadcast still works once implemented."
        />
      ) : (
        <div className="nt-stats" style={{ gridTemplateColumns: 'repeat(5, 1fr)' }}>
          <StatCard label="Notifications" value={String(stats.totalNotifications)} tile="var(--nt-sky)" />
          <StatCard label="Users" value={String(stats.totalUsers)} tile="var(--nt-purple)" />
          <StatCard label="Today" value={String(stats.notificationsToday)} tile="var(--nt-teal)" />
          <StatCard label="Unread" value={String(stats.unreadNotifications)} tile="var(--nt-orange)" />
          <StatCard label="Read rate" value={`${stats.readRatePercentage.toFixed(1)}%`} tile="var(--nt-green)" />
        </div>
      )}
      <Card elevated>
        <h3>Broadcast to all users</h3>
        <p>Posts an announcement notification to every registered user.</p>
        <form onSubmit={broadcast}>
          <TextField label="Title" required value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Scheduled maintenance" />
          <div className="nt-field">
            <label>Message</label>
            <textarea className="nt-textarea" required rows={3} value={message} onChange={(e) => setMessage(e.target.value)} placeholder="What should users know?" />
          </div>
          <Button type="submit" disabled={sending}>
            {sending ? 'Sending…' : 'Send broadcast'}
          </Button>
        </form>
      </Card>
    </>
  );
}
