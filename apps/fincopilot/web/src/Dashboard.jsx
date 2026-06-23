import React, { useEffect, useState } from 'react';
import { getMonthlyCashflow, getSpendingByCategory, getSummary } from './api.js';
import { S } from './styles.js';

export default function Dashboard() {
  const [summary, setSummary] = useState(null);
  const [categories, setCategories] = useState([]);
  const [cashflow, setCashflow] = useState([]);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([getSummary(), getSpendingByCategory(), getMonthlyCashflow()])
      .then(([s, c, f]) => {
        setSummary(s);
        setCategories(c);
        setCashflow(f);
      })
      .catch((e) => setError(e.message));
  }, []);

  if (error) {
    return <div style={S.content}><div style={S.error}>{error}</div></div>;
  }
  if (!summary) {
    return <div style={S.content}><p style={S.hint}>Loading…</p></div>;
  }
  if (summary.transactionCount === 0) {
    return <div style={S.content}><p style={S.hint}>No data yet — add transactions in the Data tab, then come back.</p></div>;
  }

  const maxCategory = Math.max(1, ...categories.map((c) => Number(c.spent)));
  const maxFlow = Math.max(1, ...cashflow.flatMap((m) => [Number(m.income), Number(m.expense)]));

  return (
    <div style={S.content}>
      <div style={S.grid}>
        <Stat label="Income" value={fmt(summary.totalIncome)} />
        <Stat label="Expense" value={fmt(summary.totalExpense)} />
        <Stat label="Net" value={fmt(summary.net)} />
        <Stat label="Transactions" value={summary.transactionCount} />
      </div>

      <h3 style={S.sectionTitle}>Spending by category</h3>
      {categories.map((c) => (
        <Bar key={c.category} label={c.category} value={Number(c.spent)} max={maxCategory} />
      ))}

      <h3 style={S.sectionTitle}>Monthly cashflow (income / expense)</h3>
      {cashflow.map((m) => (
        <div key={m.month} style={{ marginBottom: 12 }}>
          <div style={{ fontSize: 12, color: '#cbd5e1', marginBottom: 4 }}>{m.month}</div>
          <Bar label="income" value={Number(m.income)} max={maxFlow} color="#34d399" />
          <Bar label="expense" value={Number(m.expense)} max={maxFlow} color="#f87171" />
        </div>
      ))}
    </div>
  );
}

function Stat({ label, value }) {
  return (
    <div style={S.statCard}>
      <div style={S.statLabel}>{label}</div>
      <div style={S.statValue}>{value}</div>
    </div>
  );
}

function Bar({ label, value, max, color }) {
  const pct = Math.max(2, Math.round((value / max) * 100));
  return (
    <div style={S.chartRow}>
      <div style={S.barLabel}>{label}</div>
      <div style={{ ...S.bar, width: pct + '%', background: color || '#38bdf8' }} />
      <div style={S.barValue}>{fmt(value)}</div>
    </div>
  );
}

function fmt(n) {
  return Number(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
