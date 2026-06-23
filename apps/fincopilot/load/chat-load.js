// k6 load test for the FinCopilot chat endpoint — a manual soak/load artifact (not run in CI).
//
//   1. docker compose up --build           # bring up the stack (needs a reachable Ollama)
//   2. sign up to get a token, e.g.:
//      TOKEN=$(curl -s localhost:8080/api/auth/signup -H 'Content-Type: application/json' \
//        -d '{"email":"load@example.com","password":"password123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
//   3. TOKEN=$TOKEN k6 run apps/fincopilot/load/chat-load.js
//
// Thresholds fail the run if error rate or p95 latency regress — a guardrail for capacity tuning.
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: Number(__ENV.VUS || 20),
  duration: __ENV.DURATION || '1m',
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<8000'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';

export default function () {
  const res = http.post(
    `${BASE}/api/chat/turn`,
    JSON.stringify({ sessionId: `load-${__VU}`, input: 'Give me a one-sentence budgeting tip.' }),
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${TOKEN}` } },
  );
  check(res, { 'status is 200': (r) => r.status === 200 });
  sleep(1);
}
