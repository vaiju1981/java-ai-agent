// Thin client for the FinCopilot API: token-based auth + streaming chat.

const TOKEN_KEY = 'fincopilot.token';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

async function authenticate(path, email, password) {
  const res = await fetch(`/api/auth/${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) {
    const message =
      res.status === 401
        ? 'Invalid email or password.'
        : res.status === 409
          ? 'That email is already registered.'
          : 'Please enter a valid email and a password of at least 8 characters.';
    throw new Error(message);
  }
  const data = await res.json();
  setToken(data.token);
  return data.token;
}

export const login = (email, password) => authenticate('login', email, password);
export const signup = (email, password) => authenticate('signup', email, password);

export function logout() {
  const token = getToken();
  setToken(null);
  return fetch('/api/auth/logout', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  }).catch(() => {});
}

async function api(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.status === 401) {
    setToken(null);
    throw new Error('Your session expired — please sign in again.');
  }
  if (!res.ok) {
    let detail = '';
    try {
      detail = (await res.json()).message || '';
    } catch {
      // no JSON body
    }
    throw new Error(detail || `Request failed (${res.status}).`);
  }
  return res.status === 204 ? null : res.json();
}

// Analytics (dashboard).
export const getSummary = () => api('GET', '/api/analytics/summary');
export const getSpendingByCategory = () => api('GET', '/api/analytics/spending-by-category');
export const getMonthlyCashflow = () => api('GET', '/api/analytics/monthly-cashflow');

// Ledger (data).
export const listAccounts = () => api('GET', '/api/accounts');
export const createAccount = (name, type) => api('POST', '/api/accounts', { name, type });
export const addTransaction = (txn) => api('POST', '/api/transactions', txn);
export const importCsv = (accountId, csv) => api('POST', '/api/transactions/import', { accountId, csv });
export const listTransactions = () => api('GET', '/api/transactions');

// Conversation history.
export const getSessions = () => api('GET', '/api/chat/sessions');
export const getSessionMessages = (sessionId) =>
  api('GET', `/api/chat/sessions/${encodeURIComponent(sessionId)}`);
export const deleteSession = (sessionId) =>
  api('DELETE', `/api/chat/sessions/${encodeURIComponent(sessionId)}`);

// Human-in-the-loop tool approval + savings goals.
export const approveAction = (approvalId, approved) =>
  api('POST', '/api/chat/approve', { approvalId, approved });
export const getGoals = () => api('GET', '/api/goals');

/**
 * Streams one chat turn, invoking onEvent(name, data) for each SSE event
 * ('tool', 'tool_result', 'final', 'error'). Throws on auth/transport failure.
 */
export async function streamTurn(sessionId, input, onEvent) {
  const res = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: `Bearer ${getToken()}`,
    },
    body: JSON.stringify({ sessionId, input }),
  });
  if (res.status === 401) {
    setToken(null);
    throw new Error('Your session expired — please sign in again.');
  }
  if (!res.ok || !res.body) {
    throw new Error('The assistant is unavailable right now.');
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    let boundary;
    while ((boundary = buffer.indexOf('\n\n')) !== -1) {
      dispatch(buffer.slice(0, boundary), onEvent);
      buffer = buffer.slice(boundary + 2);
    }
  }
}

function dispatch(frame, onEvent) {
  let event = 'message';
  const dataLines = [];
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim());
    }
  }
  if (dataLines.length === 0) {
    return;
  }
  let data = dataLines.join('\n');
  try {
    data = JSON.parse(data);
  } catch {
    // leave data as raw text
  }
  onEvent(event, data);
}
