import { useEffect, useState } from 'react';
import { preferencesApi, type Preference } from '../api/client';
import { Badge, Button, Card, EmptyState, PageHeader } from '../components/ui';

const EVENT_TYPES = [
  'ORDER_PLACED',
  'ORDER_DELIVERED',
  'PAYMENT_SUCCESS',
  'COMMENT_ADDED',
  'LIKE_RECEIVED',
  'MENTIONED',
  'PASSWORD_RESET',
  'OTP_GENERATED',
];

const CHANNELS = ['EMAIL', 'SMS', 'PUSH', 'IN_APP', 'WEBSOCKET'];

export function Preferences({ notify }: { notify: (title: string, body: string) => void }) {
  const [prefs, setPrefs] = useState<Preference[]>([]);
  const [error, setError] = useState('');
  const byEvent = new Map(prefs.map((p) => [p.eventType, p]));

  useEffect(() => {
    preferencesApi.list().then(setPrefs).catch(() => setError('Could not load preferences.'));
  }, []);

  const toggle = async (eventType: string, channel: string, enabled: boolean) => {
    try {
      const updated = await preferencesApi.setChannel(eventType, channel, enabled);
      setPrefs((ps) => {
        const rest = ps.filter((p) => p.eventType !== eventType);
        return [...rest, updated];
      });
    } catch {
      notify('Preference failed', 'The backend rejected the change.');
    }
  };

  return (
    <>
      <PageHeader eyebrow="Settings" title="Preferences" sub="Per-event channel opt-in and quiet hours." />
      {error && <div className="nt-form-error">{error}</div>}
      {EVENT_TYPES.map((et) => {
        const pref = byEvent.get(et);
        return (
          <Card key={et}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
              <h3 style={{ margin: 0 }}>{et.replaceAll('_', ' ')}</h3>
              {pref?.quietHoursEnabled && <Badge tone="WARNING">quiet hours</Badge>}
            </div>
            {pref == null ? (
              <EmptyState glyph="☷" title="Defaults apply" body="All channels are opted in until you change them." />
            ) : (
              <div className="nt-toolbar">
                {CHANNELS.map((ch) => {
                  const on = pref.channelOptIn[ch] ?? true;
                  return (
                    <Button key={ch} variant={on ? 'primary' : 'utility'} onClick={() => void toggle(et, ch, !on)}>
                      {ch} {on ? '· on' : '· off'}
                    </Button>
                  );
                })}
              </div>
            )}
          </Card>
        );
      })}
      <div style={{ height: 12 }} />
    </>
  );
}
