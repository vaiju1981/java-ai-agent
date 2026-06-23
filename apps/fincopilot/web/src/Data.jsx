import React, { useEffect, useState } from 'react';
import { addTransaction, createAccount, importCsv, listAccounts, listTransactions } from './api.js';
import { S } from './styles.js';

export default function Data() {
  const [accounts, setAccounts] = useState([]);
  const [accountId, setAccountId] = useState('');
  const [txns, setTxns] = useState([]);
  const [error, setError] = useState('');
  const [msg, setMsg] = useState('');

  const [date, setDate] = useState('');
  const [amount, setAmount] = useState('');
  const [merchant, setMerchant] = useState('');
  const [category, setCategory] = useState('');
  const [csv, setCsv] = useState('');

  async function refresh() {
    try {
      const accs = await listAccounts();
      setAccounts(accs);
      if (accs.length && !accountId) {
        setAccountId(accs[0].id);
      }
      setTxns(await listTransactions());
    } catch (e) {
      setError(e.message);
    }
  }

  useEffect(() => {
    refresh();
  }, []);

  async function run(fn, ok) {
    setError('');
    setMsg('');
    try {
      await fn();
      setMsg(ok);
      await refresh();
    } catch (e) {
      setError(e.message);
    }
  }

  const addAccount = () =>
    run(async () => {
      const acc = await createAccount('Checking', 'checking');
      setAccountId(acc.id);
    }, 'Account created.');

  const submitTxn = () =>
    run(
      () => addTransaction({ accountId, date, amount, merchant, category }),
      'Transaction added.',
    ).then(() => {
      setAmount('');
      setMerchant('');
      setCategory('');
    });

  const submitCsv = () => run(() => importCsv(accountId, csv), 'CSV imported.').then(() => setCsv(''));

  return (
    <div style={S.content}>
      {error && <div style={S.error}>{error}</div>}
      {msg && <div style={S.msg}>{msg}</div>}

      {accounts.length === 0 ? (
        <div>
          <p style={S.hint}>Create an account to start adding transactions.</p>
          <button style={S.primary} onClick={addAccount}>Create a checking account</button>
        </div>
      ) : (
        <>
          <div style={S.formRow}>
            <label style={S.statLabel}>Account</label>
            <select style={S.small} value={accountId} onChange={(e) => setAccountId(e.target.value)}>
              {accounts.map((a) => (
                <option key={a.id} value={a.id}>{a.name}</option>
              ))}
            </select>
            <button style={S.navBtn} onClick={addAccount}>+ account</button>
          </div>

          <h3 style={S.sectionTitle}>Add a transaction</h3>
          <div style={S.formRow}>
            <input style={S.small} type="date" value={date} onChange={(e) => setDate(e.target.value)} />
            <input style={S.small} placeholder="amount (− expense)" value={amount} onChange={(e) => setAmount(e.target.value)} />
            <input style={S.small} placeholder="merchant" value={merchant} onChange={(e) => setMerchant(e.target.value)} />
            <input style={S.small} placeholder="category" value={category} onChange={(e) => setCategory(e.target.value)} />
            <button style={S.navBtn} onClick={submitTxn}>Add</button>
          </div>

          <h3 style={S.sectionTitle}>Import CSV</h3>
          <p style={{ fontSize: 12, color: '#64748b', marginTop: 0 }}>
            Columns: <code>date,amount,merchant,category,description</code>
          </p>
          <textarea
            style={{ ...S.small, width: '100%', boxSizing: 'border-box', height: 90, fontFamily: 'monospace' }}
            placeholder={'date,amount,merchant,category\n2026-01-15,-45.99,Whole Foods,groceries'}
            value={csv}
            onChange={(e) => setCsv(e.target.value)}
          />
          <div style={{ marginTop: 8 }}>
            <button style={S.navBtn} onClick={submitCsv}>Import</button>
          </div>

          <h3 style={S.sectionTitle}>Transactions ({txns.length})</h3>
          <table style={S.table}>
            <thead>
              <tr>
                <th style={S.th}>Date</th>
                <th style={S.th}>Amount</th>
                <th style={S.th}>Merchant</th>
                <th style={S.th}>Category</th>
              </tr>
            </thead>
            <tbody>
              {txns.slice(0, 100).map((t) => (
                <tr key={t.id}>
                  <td style={S.td}>{t.date}</td>
                  <td style={{ ...S.td, color: Number(t.amount) < 0 ? '#f87171' : '#34d399' }}>{t.amount}</td>
                  <td style={S.td}>{t.merchant}</td>
                  <td style={S.td}>{t.category}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
}
