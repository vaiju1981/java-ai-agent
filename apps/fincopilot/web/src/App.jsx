import React, { useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import {
  approveAction,
  deleteSession,
  getGoals,
  getSessionMessages,
  getSessions,
  getToken,
  login,
  logout,
  signup,
  streamTurn,
} from './api.js';
import Dashboard from './Dashboard.jsx';
import Data from './Data.jsx';
import { S } from './styles.js';

const VIEWS = ['chat', 'dashboard', 'data', 'goals', 'history'];
const SESSION_KEY = 'fincopilot.session';

// Conversation ids are tenant-scoped (history is filtered by user id, not by guessing the id), but use
// the Web Crypto API anyway — unguessable and collision-resistant, and avoids weak-randomness warnings.
function newSessionId() {
  const bytes = new Uint8Array(8);
  crypto.getRandomValues(bytes);
  return 's-' + Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

function currentSessionId() {
  let id = localStorage.getItem(SESSION_KEY);
  if (!id) {
    id = newSessionId();
    localStorage.setItem(SESSION_KEY, id);
  }
  return id;
}

export default function App() {
  const [token, setTok] = useState(getToken());
  const [view, setView] = useState('chat');
  const [sessionId, setSessionId] = useState(currentSessionId);

  if (!token) {
    return <Auth onAuthed={setTok} />;
  }

  function openSession(id) {
    localStorage.setItem(SESSION_KEY, id);
    setSessionId(id);
    setView('chat');
  }

  function startNewChat() {
    openSession(newSessionId());
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
      {view === 'chat' && <Chat key={sessionId} sessionId={sessionId} onNewChat={startNewChat} />}
      {view === 'dashboard' && <Dashboard />}
      {view === 'data' && <Data />}
      {view === 'goals' && <Goals />}
      {view === 'history' && <History onOpen={openSession} />}
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

function Chat({ sessionId, onNewChat }) {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState(null);

  // Load this session's prior messages so a refresh or a resumed session shows its history.
  useEffect(() => {
    let active = true;
    getSessionMessages(sessionId)
      .then((past) => {
        if (active && past && past.length > 0) {
          setMessages(past.map((m) => ({ role: m.role, text: m.content })));
        }
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [sessionId]);

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
        } else if (event === 'tool_data') {
          try {
            setMessages((m) => [...m, { role: 'tool_data', payload: JSON.parse(data.data) }]);
          } catch {
            // ignore an unparseable payload
          }
        } else if (event === 'approval_required') {
          setPending({ approvalId: data.approvalId, name: data.name, args: data.arguments });
        } else if (event === 'final') {
          setPending(null);
          setMessages((m) => [...m, { role: 'assistant', text: data.output, tools: [...tools] }]);
        } else if (event === 'error') {
          setPending(null);
          setMessages((m) => [...m, { role: 'assistant', text: 'Something went wrong handling that.', error: true }]);
        }
      });
    } catch (e) {
      setPending(null);
      setMessages((m) => [...m, { role: 'assistant', text: e.message, error: true }]);
    } finally {
      setBusy(false);
    }
  }

  async function resolve(approved) {
    const action = pending;
    setPending(null);
    if (!action) {
      return;
    }
    try {
      await approveAction(action.approvalId, approved);
    } catch {
      // if this fails the turn simply times out and is declined
    }
  }

  return (
    <>
      <div style={S.chatBar}>
        <span style={S.hintSmall}>Conversation {sessionId}</span>
        <button style={S.link} onClick={onNewChat}>New chat</button>
      </div>
      <main style={S.messages}>
        {messages.length === 0 && <p style={S.hint}>Ask about your finances — e.g. “How much did I spend on groceries?”</p>}
        {messages.map((m, i) =>
          m.role === 'tool_data' ? (
            <ToolDataCard key={i} payload={m.payload} />
          ) : (
            <div key={i} style={m.role === 'user' ? S.user : S.assistant}>
              {m.tools && m.tools.length > 0 && <div style={S.tools}>used: {m.tools.join(', ')}</div>}
              {m.error ? (
                <div style={S.errText}>{m.text}</div>
              ) : m.role === 'user' ? (
                <div>{m.text}</div>
              ) : (
                <Markdown text={m.text} />
              )}
            </div>
          ),
        )}
        {busy && !pending && <div style={S.assistant}><em>thinking…</em></div>}
      </main>
      {pending && (
        <div style={S.approvalCard}>
          <div>
            FinCopilot wants to run <b>{pending.name}</b>
            {pending.args && pending.args !== '{}' ? <span style={S.hintSmall}> with {pending.args}</span> : null}.
            Approve?
          </div>
          <div style={S.row}>
            <button style={S.primary} onClick={() => resolve(true)}>Approve</button>
            <button style={S.secondary} onClick={() => resolve(false)}>Reject</button>
          </div>
        </div>
      )}
      <footer style={S.composer}>
        <input style={S.composerInput} placeholder="Message FinCopilot…" value={input} disabled={busy}
          onChange={(e) => setInput(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && send()} />
        <button style={S.primary} onClick={send} disabled={busy}>Send</button>
      </footer>
    </>
  );
}

function History({ onOpen }) {
  const [sessions, setSessions] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getSessions()
      .then(setSessions)
      .catch((e) => setError(e.message));
  }, []);

  async function remove(sessionId) {
    try {
      await deleteSession(sessionId);
      setSessions((prev) => prev.filter((s) => s.sessionId !== sessionId));
    } catch (e) {
      setError(e.message);
    }
  }

  return (
    <main style={S.messages}>
      {error && <div style={S.error}>{error}</div>}
      {!error && sessions === null && <p style={S.hint}>Loading…</p>}
      {!error && sessions !== null && sessions.length === 0 && (
        <p style={S.hint}>No past conversations yet.</p>
      )}
      {sessions &&
        sessions.map((s) => (
          <div key={s.sessionId} style={S.sessionRow}>
            <button style={S.sessionOpen} onClick={() => onOpen(s.sessionId)}>
              <span>Conversation {s.sessionId}</span>
              <span style={S.hintSmall}>
                {s.messageCount} messages · {new Date(s.lastActivityMillis).toLocaleString()}
              </span>
            </button>
            <button style={S.deleteBtn} title="Delete conversation" onClick={() => remove(s.sessionId)}>
              ✕
            </button>
          </div>
        ))}
    </main>
  );
}

function Goals() {
  const [goals, setGoals] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getGoals()
      .then(setGoals)
      .catch((e) => setError(e.message));
  }, []);

  return (
    <main style={S.content}>
      <h2 style={S.sectionTitle}>Savings goals</h2>
      {error && <div style={S.error}>{error}</div>}
      {!error && goals === null && <p style={S.hint}>Loading…</p>}
      {!error && goals !== null && goals.length === 0 && (
        <p style={S.hint}>No goals yet. Ask FinCopilot in chat to set one — you’ll be asked to approve it.</p>
      )}
      {goals &&
        goals.map((g) => (
          <div key={g.id} style={S.statCard}>
            <div style={S.statLabel}>
              {g.name}
              {g.targetDate ? ` · by ${g.targetDate}` : ''}
            </div>
            <div style={S.statValue}>{fmt(g.targetAmount)}</div>
          </div>
        ))}
    </main>
  );
}

// Full markdown for assistant answers — react-markdown is XSS-safe by default (it never renders raw HTML).
// Element overrides keep it tight and on-theme; links open safely in a new tab.
const MD_COMPONENTS = {
  p: (props) => <p style={{ margin: '0 0 8px' }} {...strip(props)} />,
  ul: (props) => <ul style={{ margin: '4px 0', paddingLeft: 18 }} {...strip(props)} />,
  ol: (props) => <ol style={{ margin: '4px 0', paddingLeft: 18 }} {...strip(props)} />,
  li: (props) => <li style={{ margin: '2px 0' }} {...strip(props)} />,
  h1: (props) => <h3 style={S.mdHeading} {...strip(props)} />,
  h2: (props) => <h3 style={S.mdHeading} {...strip(props)} />,
  h3: (props) => <h3 style={S.mdHeading} {...strip(props)} />,
  pre: (props) => <pre style={S.mdPre} {...strip(props)} />,
  code: (props) => <code style={S.code} {...strip(props)} />,
  a: (props) => <a target="_blank" rel="noopener noreferrer" {...strip(props)} />,
};

// Drop react-markdown's internal `node` prop so it isn't spread onto the DOM element.
function strip({ node, ...rest }) {
  return rest;
}

function Markdown({ text }) {
  return (
    <div style={S.markdown}>
      <ReactMarkdown components={MD_COMPONENTS}>{String(text == null ? '' : text)}</ReactMarkdown>
    </div>
  );
}

function fmt(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n.toLocaleString(undefined, { maximumFractionDigits: 2 }) : String(value);
}

// Renders a StructuredTool's payload (tool_data SSE event) inline in the chat — see FW-3.
function ToolDataCard({ payload }) {
  if (!payload || !Array.isArray(payload.items) || payload.items.length === 0) {
    return null;
  }
  if (payload.type === 'spending_by_category') {
    const max = Math.max(...payload.items.map((it) => Math.abs(Number(it.value) || 0)), 1);
    return (
      <div style={S.dataCard}>
        <div style={S.dataTitle}>Spending by category</div>
        {payload.items.map((it, i) => (
          <div key={i} style={S.chartRow}>
            <span style={S.barLabel}>{it.label}</span>
            <span
              style={{ ...S.bar, width: `${(Math.abs(Number(it.value) || 0) / max) * 60 + 2}%`, background: '#38bdf8' }} />
            <span style={S.barValue}>{fmt(it.value)}</span>
          </div>
        ))}
      </div>
    );
  }
  if (payload.type === 'monthly_cashflow') {
    return (
      <div style={S.dataCard}>
        <div style={S.dataTitle}>Monthly cashflow</div>
        <table style={S.table}>
          <thead>
            <tr>
              <th style={S.th}>Month</th>
              <th style={S.th}>Income</th>
              <th style={S.th}>Expense</th>
            </tr>
          </thead>
          <tbody>
            {payload.items.map((m, i) => (
              <tr key={i}>
                <td style={S.td}>{m.month}</td>
                <td style={S.td}>{fmt(m.income)}</td>
                <td style={S.td}>{fmt(m.expense)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }
  return null;
}
