import React, { useState } from 'react';
import { getToken, login, logout, signup, streamTurn } from './api.js';

export default function App() {
  const [token, setTok] = useState(getToken());
  if (!token) {
    return <Auth onAuthed={setTok} />;
  }
  return (
    <Chat
      onLogout={() => {
        logout();
        setTok(null);
      }}
    />
  );
}

function Auth({ onAuthed }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(action) {
    setBusy(true);
    setError('');
    try {
      onAuthed(await action(email, password));
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={S.center}>
      <div style={S.card}>
        <h1 style={S.brand}>FinCopilot</h1>
        <p style={S.tagline}>Your grounded finance copilot.</p>
        <input
          style={S.input}
          type="email"
          placeholder="Email"
          value={email}
          autoComplete="email"
          onChange={(e) => setEmail(e.target.value)}
        />
        <input
          style={S.input}
          type="password"
          placeholder="Password (min 8 characters)"
          value={password}
          autoComplete="current-password"
          onChange={(e) => setPassword(e.target.value)}
        />
        {error && <div style={S.error}>{error}</div>}
        <div style={S.row}>
          <button style={S.primary} disabled={busy} onClick={() => submit(login)}>
            Sign in
          </button>
          <button style={S.secondary} disabled={busy} onClick={() => submit(signup)}>
            Create account
          </button>
        </div>
        <p style={S.disclaimer}>Information and analysis only — not regulated financial advice.</p>
      </div>
    </div>
  );
}

function Chat({ onLogout }) {
  const [sessionId] = useState(() => 's-' + Math.random().toString(36).slice(2, 10));
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);

  async function send() {
    const text = input.trim();
    if (!text || busy) {
      return;
    }
    setInput('');
    setBusy(true);
    setMessages((m) => [...m, { role: 'user', text }]);
    const tools = [];
    try {
      await streamTurn(sessionId, text, (event, data) => {
        if (event === 'tool') {
          tools.push(data.name);
        } else if (event === 'final') {
          setMessages((m) => [...m, { role: 'assistant', text: data.output, tools: [...tools] }]);
        } else if (event === 'error') {
          setMessages((m) => [...m, { role: 'assistant', text: 'Something went wrong handling that.', error: true }]);
        }
      });
    } catch (e) {
      setMessages((m) => [...m, { role: 'assistant', text: e.message, error: true }]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={S.app}>
      <header style={S.header}>
        <strong>FinCopilot</strong>
        <button style={S.link} onClick={onLogout}>
          Sign out
        </button>
      </header>
      <main style={S.messages}>
        {messages.length === 0 && <p style={S.hint}>Ask about your finances — e.g. “What can you help me with?”</p>}
        {messages.map((m, i) => (
          <div key={i} style={m.role === 'user' ? S.user : S.assistant}>
            {m.tools && m.tools.length > 0 && <div style={S.tools}>used: {m.tools.join(', ')}</div>}
            <div style={m.error ? S.errText : undefined}>{m.text}</div>
          </div>
        ))}
        {busy && (
          <div style={S.assistant}>
            <em>thinking…</em>
          </div>
        )}
      </main>
      <footer style={S.composer}>
        <input
          style={S.composerInput}
          placeholder="Message FinCopilot…"
          value={input}
          disabled={busy}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && send()}
        />
        <button style={S.primary} onClick={send} disabled={busy}>
          Send
        </button>
      </footer>
    </div>
  );
}

const S = {
  center: { minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#0f172a', fontFamily: 'system-ui, sans-serif' },
  card: { width: 360, padding: 32, background: '#1e293b', borderRadius: 12, color: '#e2e8f0', boxShadow: '0 10px 40px rgba(0,0,0,0.4)' },
  brand: { margin: '0 0 4px', fontSize: 28, color: '#38bdf8' },
  tagline: { margin: '0 0 20px', color: '#94a3b8', fontSize: 14 },
  input: { width: '100%', boxSizing: 'border-box', padding: '10px 12px', marginBottom: 12, borderRadius: 8, border: '1px solid #334155', background: '#0f172a', color: '#e2e8f0', fontSize: 14 },
  row: { display: 'flex', gap: 8 },
  primary: { flex: 1, padding: '10px 12px', borderRadius: 8, border: 'none', background: '#38bdf8', color: '#0f172a', fontWeight: 600, cursor: 'pointer' },
  secondary: { flex: 1, padding: '10px 12px', borderRadius: 8, border: '1px solid #334155', background: 'transparent', color: '#e2e8f0', cursor: 'pointer' },
  error: { color: '#f87171', fontSize: 13, marginBottom: 12 },
  disclaimer: { marginTop: 16, fontSize: 11, color: '#64748b' },
  app: { display: 'flex', flexDirection: 'column', height: '100vh', background: '#0f172a', color: '#e2e8f0', fontFamily: 'system-ui, sans-serif' },
  header: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '14px 20px', borderBottom: '1px solid #1e293b', color: '#38bdf8' },
  link: { background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer', fontSize: 13 },
  messages: { flex: 1, overflowY: 'auto', padding: 20, display: 'flex', flexDirection: 'column', gap: 12 },
  hint: { color: '#64748b', textAlign: 'center', marginTop: 40 },
  user: { alignSelf: 'flex-end', maxWidth: '75%', padding: '10px 14px', borderRadius: 12, background: '#38bdf8', color: '#0f172a' },
  assistant: { alignSelf: 'flex-start', maxWidth: '75%', padding: '10px 14px', borderRadius: 12, background: '#1e293b' },
  tools: { fontSize: 11, color: '#94a3b8', marginBottom: 4 },
  errText: { color: '#f87171' },
  composer: { display: 'flex', gap: 8, padding: 16, borderTop: '1px solid #1e293b' },
  composerInput: { flex: 1, padding: '10px 14px', borderRadius: 8, border: '1px solid #334155', background: '#1e293b', color: '#e2e8f0', fontSize: 14 },
};
