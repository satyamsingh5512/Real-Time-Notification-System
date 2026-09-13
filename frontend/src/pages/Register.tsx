import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';
import { Button, TextField } from '../components/ui';

export function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await register(email, password, displayName);
      navigate('/');
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 400
          ? 'That email is taken or the password is too short (min 8 characters).'
          : 'Could not create the account. Is the backend running?',
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="nt-container">
      <div className="nt-auth-wrap">
        <div className="nt-auth-card">
          <h1>Create your workspace</h1>
          <p className="nt-auth-sub">One account for live notifications everywhere.</p>
          {error && <div className="nt-form-error">{error}</div>}
          <form onSubmit={submit}>
            <TextField label="Display name" required value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="Ada Lovelace" />
            <TextField label="Email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
            <TextField label="Password" type="password" required minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} placeholder="At least 8 characters" />
            <Button type="submit" block disabled={busy}>
              {busy ? 'Creating…' : 'Get started'}
            </Button>
          </form>
          <p className="nt-auth-switch">
            Already have an account? <Link to="/login">Log in</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
