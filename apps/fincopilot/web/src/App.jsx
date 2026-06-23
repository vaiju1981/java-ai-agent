import React, { useState } from 'react';
import { getToken, login, logout, signup, streamTurn } from './api.js';
import Dashboard from './Dashboard.jsx';
import Data from './Data.jsx';
import { S } from './styles.js';

const VIEWS = ['chat', 'dashboard', 'data'];

export default function App() {
  const [token, setTok] = useState(getToken());
  const [view, setView] = useState('chat');

  if (!token) {
    return <Auth onAuthed={setTok} />;
  }

  return (
    <div style={S.app}>
      <header style={S.header}>
        <span style={S.brandSmall}>FinCopilot</span>
        <nav style={S.nav}>
          {VIEWS.map((v) => (
            <button key={v} style={view === v ? S.navBtnActive : S.navBtn} onClick={() => setView(v)}>
              {v[0].toUpperCase() + v.slice(1)}
            </button>
          ))}
        </nav>
        <button
          style={S.link}
          onClick={() => {
            logout();
            setTok(null);
          }}>
          Sign out
        </button>
      </header>
      {view === 'chat' && <Chat />}
      {view === 'dashboard' && <Dashboard />}
      {view === 'data' && <Data />}
    </div>
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
        <input style={S.input} type="email" placeholder="Email" value={email}
          autoComplete="email" onChange={(e) => setEmail(e.target.value)} />
        <input style={S.input} type="password" placeholder="Password (min 8 characters)" value={password}
          autoComplete="current-password" onChange={(e) => setPassword(e.target.value)} />
        {error && <div style={S.error}>{error}</div>}
        <div style={S.row}>
          <button style={S.primary} disabled={busy} onClick={() => submit(login)}>Sign in</button>
          <button style={S.secondary} disabled={busy} onClick={() => submit(signup)}>Create account</button>
        </div>
        <p style={S.disclaimer}>Information and analysis only — not regulated financial advice.</p>
      </div>
    </div>
  );
}

function Chat() {
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
    <>
      <main style={S.messages}>
        {messages.length === 0 && <p style={S.hint}>Ask about your finances — e.g. “How much did I spend on groceries?”</p>}
        {messages.map((m, i) => (
          <div key={i} style={m.role === 'user' ? S.user : S.assistant}>
            {m.tools && m.tools.length > 0 && <div style={S.tools}>used: {m.tools.join(', ')}</div>}
            <div style={m.error ? S.errText : undefined}>{m.text}</div>
          </div>
        ))}
        {busy && <div style={S.assistant}><em>thinking…</em></div>}
      </main>
      <footer style={S.composer}>
        <input style={S.composerInput} placeholder="Message FinCopilot…" value={input} disabled={busy}
          onChange={(e) => setInput(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && send()} />
        <button style={S.primary} onClick={send} disabled={busy}>Send</button>
      </footer>
    </>
  );
}
