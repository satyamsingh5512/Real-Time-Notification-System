import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';
import { Button, TextField } from '../components/ui';

export function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await login(email, password);
      navigate('/');
    } catch (err) {
      setError(err instanceof ApiError && err.status === 401 ? 'Invalid email or password.' : 'Could not sign in. Is the backend running?');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="nt-container">
      <div className="nt-grid-2" style={{ alignItems: 'stretch', marginTop: 48 }}>
        <div className="nt-auth-wrap" style={{ margin: 0, width: '100%', maxWidth: 480 }}>
          <div className="nt-auth-card">
            <h1>Welcome back</h1>
            <p className="nt-auth-sub">Sign in to your notification workspace.</p>
            {error && <div className="nt-form-error">{error}</div>}
            <form onSubmit={submit}>
              <TextField label="Email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
              <TextField label="Password" type="password" required value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
              <Button type="submit" block disabled={busy}>
                {busy ? 'Signing in…' : 'Log in'}
              </Button>
            </form>
            <p className="nt-auth-switch">
              New here? <Link to="/register">Create an account</Link>
            </p>
          </div>
        </div>
        <div className="nt-hero-band">
          <div className="nt-stickers">
            <span className="nt-sticker" style={{ background: 'var(--nt-purple)' }} />
            <span className="nt-sticker" style={{ background: 'var(--nt-pink)' }} />
            <span className="nt-sticker" style={{ background: 'var(--nt-orange)' }} />
            <span className="nt-sticker" style={{ background: 'var(--nt-teal)' }} />
            <span className="nt-sticker" style={{ background: 'var(--nt-green)' }} />
          </div>
          <h2>Every event, delivered live.</h2>
          <p>Orders, payments, mentions and OTPs stream to your inbox over WebSocket — no refresh required.</p>
          <div className="nt-badge" style={{ background: 'rgba(255,255,255,.12)', borderColor: 'transparent', color: '#fff' }}>
            10K+ events / hour
          </div>
        </div>
      </div>
    </div>
  );
}
