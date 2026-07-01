import { LineChart, Pin, Play, Table2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type { AnalysisKind, AnalysisRequest, AnalysisResponse, AppSnapshot, SchemaSummary } from "../../api/analyticsApi";
import { loadSchemaSummary, runAnalysis } from "../../api/analyticsApi";
import { ResultTable } from "../../components/ResultTable";
import { fileName, formatValue } from "../../lib/format";

type AnalysisWorkspaceScreenProps = {
  snapshot: AppSnapshot;
};

type AnalysisTab = {
  id: AnalysisKind;
  label: string;
  description: string;
};

type PinnedCandidate = AnalysisResponse["tileCandidate"] & {
  pinnedAt: string;
};

const analysisTabs: AnalysisTab[] = [
  { id: "profile", label: "Profile", description: "Schema, missingness, and distribution summary." },
  { id: "correlations", label: "Correlations", description: "Rank numeric relationships against a target." },
  { id: "features", label: "Features", description: "Rank drivers using correlation and categorical lift." },
  { id: "segments", label: "Segments", description: "Compare target behavior across a dimension." },
  { id: "regression", label: "Regression", description: "Screen single-feature linear models and diagnostics." },
  { id: "anomalies", label: "Anomalies", description: "Find unusual values using a z-score scan." },
  { id: "forecast", label: "Forecast", description: "Build a bounded linear trend baseline." },
  { id: "causal-check", label: "Causal Check", description: "Surface causal caveats for observational data." }
];

const tableKeys = [
  "features",
  "correlations",
  "groups",
  "columns",
  "forecast",
  "anomalies",
  "featureImportance",
  "rows"
];

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function rowsFromResult(result: unknown): { label: string; rows: Array<Record<string, unknown>> } | null {
  if (!isRecord(result)) {
    return null;
  }
  for (const key of tableKeys) {
    const value = result[key];
    if (Array.isArray(value) && value.every(isRecord)) {
      return { label: key, rows: value };
    }
  }
  return null;
}

function summaryRows(result: unknown): Array<Record<string, unknown>> {
  if (!isRecord(result)) {
    return [];
  }
  return Object.entries(result)
    .filter(([, value]) => typeof value === "string" || typeof value === "number" || typeof value === "boolean")
    .map(([metric, value]) => ({ metric, value }));
}

function preferredColumn(columns: string[], preferred: string, fallbackIndex = 0) {
  return columns.includes(preferred) ? preferred : columns[fallbackIndex] ?? "";
}

function numericValue(row: Record<string, unknown>) {
  for (const key of ["score", "abs", "mean", "revenue", "avg_revenue", "value", "r2"]) {
    const value = row[key];
    if (typeof value === "number") {
      return value;
    }
  }
  const discovered = Object.values(row).find((value) => typeof value === "number");
  return typeof discovered === "number" ? discovered : 0;
}

function labelValue(row: Record<string, unknown>) {
  for (const key of ["feature", "column", "segment", "channel", "week", "timeColumn", "metric"]) {
    const value = row[key];
    if (value !== undefined && value !== null) {
      return String(value);
    }
  }
  const discovered = Object.values(row).find((value) => typeof value === "string");
  return discovered ? String(discovered) : "row";
}

function ChartPreview({ rows }: { rows: Array<Record<string, unknown>> }) {
  const chartRows = rows.slice(0, 8).map((row) => ({
    label: labelValue(row),
    value: Math.abs(numericValue(row))
  }));
  const max = Math.max(...chartRows.map((row) => row.value), 1);

  if (chartRows.length === 0) {
    return <div className="empty-state">No chartable rows returned.</div>;
  }

  return (
    <div className="analysis-chart">
      {chartRows.map((row) => (
        <div className="bar-row" key={row.label}>
          <span>{row.label}</span>
          <div className="bar-track">
            <div className="bar-fill" style={{ width: `${Math.max(4, (row.value / max) * 100)}%` }} />
          </div>
          <strong>{formatValue(row.value)}</strong>
        </div>
      ))}
    </div>
  );
}

function SelectControl({
  label,
  onChange,
  options,
  value
}: {
  label: string;
  onChange: (value: string) => void;
  options: string[];
  value: string;
}) {
  return (
    <label className="control-field">
      <span>{label}</span>
      <select onChange={(event) => onChange(event.target.value)} value={value}>
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </label>
  );
}

export function AnalysisWorkspaceScreen({ snapshot }: AnalysisWorkspaceScreenProps) {
  const [schema, setSchema] = useState<SchemaSummary | null>(null);
  const [active, setActive] = useState<AnalysisKind>("profile");
  const [config, setConfig] = useState<AnalysisRequest>({});
  const [response, setResponse] = useState<AnalysisResponse | null>(null);
  const [pinned, setPinned] = useState<PinnedCandidate[]>([]);
  const [loadingSchema, setLoadingSchema] = useState(true);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoadingSchema(true);
    setError(null);
    loadSchemaSummary()
      .then((next) => {
        if (!cancelled) {
          setSchema(next);
          setConfig({});
          setResponse(null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Unable to load analysis schema");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingSchema(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [snapshot.session?.csv]);

  const columns = schema?.columns ?? [];
  const numericColumns = columns.filter((column) => column.type === "numeric").map((column) => column.name);
  const categoricalColumns = columns.filter((column) => column.type !== "numeric").map((column) => column.name);
  const target = config.target ?? preferredColumn(numericColumns, "revenue");
  const segment = config.segment ?? preferredColumn(categoricalColumns, "channel");
  const treatment = config.treatment ?? preferredColumn(numericColumns, "discount_rate");
  const outcome = config.outcome ?? target;
  const timeColumn = config.timeColumn ?? preferredColumn(numericColumns, "week");
  const periods = config.periods ?? 4;
  const regressionFeatures = useMemo(
    () => numericColumns.filter((column) => column !== target).slice(0, 3),
    [numericColumns, target]
  );
  const table = rowsFromResult(response?.result);
  const metrics = summaryRows(response?.result);

  function requestFor(kind: AnalysisKind): AnalysisRequest {
    if (kind === "profile") {
      return {};
    }
    if (kind === "segments") {
      return { segment, target };
    }
    if (kind === "causal-check") {
      return { treatment, outcome };
    }
    if (kind === "forecast") {
      return { target, timeColumn, periods };
    }
    if (kind === "regression") {
      return { target, features: regressionFeatures };
    }
    return { target };
  }

  async function execute(kind = active) {
    if (loadingSchema || running) {
      return;
    }
    setRunning(true);
    setError(null);
    try {
      setResponse(await runAnalysis(kind, requestFor(kind)));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Analysis failed");
    } finally {
      setRunning(false);
    }
  }

  function pinCandidate() {
    if (!response) {
      return;
    }
    setPinned((current) => [
      {
        ...response.tileCandidate,
        pinnedAt: new Date().toLocaleTimeString()
      },
      ...current.filter((item) => item.id !== response.tileCandidate.id)
    ]);
  }

  const selectedTab = analysisTabs.find((tab) => tab.id === active) ?? analysisTabs[0];

  return (
    <div className="analysis-layout">
      <section className="panel hero-panel">
        <div>
          <p className="eyebrow">Analysis Workspace</p>
          <h2>{fileName(snapshot.session?.csv)}</h2>
          <p className="muted">
            {formatValue(snapshot.session?.rows)} rows, {numericColumns.length} numeric columns,{" "}
            {categoricalColumns.length} categorical columns.
          </p>
        </div>
        <div className="hero-stats">
          <div>
            <span>{analysisTabs.length}</span>
            <small>tools</small>
          </div>
          <div>
            <span>{pinned.length}</span>
            <small>pinned candidates</small>
          </div>
        </div>
      </section>

      <section className="analysis-shell">
        <aside className="analysis-tabs panel">
          {analysisTabs.map((tab) => (
            <button
              aria-current={active === tab.id ? "page" : undefined}
              key={tab.id}
              onClick={() => {
                setActive(tab.id);
                setResponse(null);
                setError(null);
              }}
              type="button"
            >
              <strong>{tab.label}</strong>
              <span>{tab.description}</span>
            </button>
          ))}
        </aside>

        <section className="workspace-grid">
          <section className="panel">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Tool Setup</p>
                <h3>{selectedTab.label}</h3>
              </div>
              <span className="pill">{running ? "Running" : "Ready"}</span>
            </div>

            <div className="analysis-controls">
              {active !== "profile" && active !== "causal-check" && (
                <SelectControl
                  label="Target"
                  onChange={(value) => setConfig((current) => ({ ...current, target: value }))}
                  options={numericColumns}
                  value={target}
                />
              )}
              {active === "segments" && (
                <SelectControl
                  label="Segment"
                  onChange={(value) => setConfig((current) => ({ ...current, segment: value }))}
                  options={categoricalColumns}
                  value={segment}
                />
              )}
              {active === "forecast" && (
                <>
                  <SelectControl
                    label="Time Column"
                    onChange={(value) => setConfig((current) => ({ ...current, timeColumn: value }))}
                    options={numericColumns}
                    value={timeColumn}
                  />
                  <label className="control-field">
                    <span>Periods</span>
                    <input
                      max={12}
                      min={1}
                      onChange={(event) => setConfig((current) => ({ ...current, periods: Number(event.target.value) }))}
                      type="number"
                      value={periods}
                    />
                  </label>
                </>
              )}
              {active === "causal-check" && (
                <>
                  <SelectControl
                    label="Treatment"
                    onChange={(value) => setConfig((current) => ({ ...current, treatment: value }))}
                    options={[...numericColumns, ...categoricalColumns]}
                    value={treatment}
                  />
                  <SelectControl
                    label="Outcome"
                    onChange={(value) => setConfig((current) => ({ ...current, outcome: value }))}
                    options={numericColumns}
                    value={outcome}
                  />
                </>
              )}
              <button
                className="primary-button"
                disabled={loadingSchema || running || (active !== "profile" && numericColumns.length === 0)}
                onClick={() => void execute()}
                type="button"
              >
                <Play size={16} />
                Run Analysis
              </button>
            </div>

            {active === "regression" && (
              <p className="muted">Features: {regressionFeatures.length > 0 ? regressionFeatures.join(", ") : "none available"}</p>
            )}
            {error && <div className="chat-warning">{error}</div>}
          </section>

          {response ? (
            <>
              <section className="panel">
                <div className="section-heading">
                  <div>
                    <p className="eyebrow">Result</p>
                    <h3>{response.title}</h3>
                  </div>
                  <button className="icon-button" onClick={pinCandidate} type="button">
                    <Pin size={16} />
                    Pin Candidate
                  </button>
                </div>
                <p className="muted">{response.description}</p>
                {metrics.length > 0 && <ResultTable rows={metrics} />}
                {table ? <ResultTable rows={table.rows} /> : <pre className="json-block">{JSON.stringify(response.result, null, 2)}</pre>}
              </section>

              <section className="panel">
                <div className="section-heading">
                  <div>
                    <p className="eyebrow">Chart Preview</p>
                    <h3>{response.visualization}</h3>
                  </div>
                  <LineChart size={18} />
                </div>
                {table ? <ChartPreview rows={table.rows} /> : <div className="empty-state">This result is best represented as a governance card.</div>}
              </section>
            </>
          ) : (
            <section className="panel placeholder-panel">
              <p className="eyebrow">Result</p>
              <h2>{loadingSchema ? "Loading analysis schema" : "Run an analysis"}</h2>
              <p className="muted">{selectedTab.description}</p>
            </section>
          )}

          <section className="panel">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Dashboard Candidates</p>
                <h3>Pinned In This Workspace</h3>
              </div>
              <Table2 size={18} />
            </div>
            {pinned.length === 0 ? (
              <div className="empty-state">No candidates pinned yet.</div>
            ) : (
              <div className="candidate-list">
                {pinned.map((candidate) => (
                  <article className="candidate-item" key={candidate.id}>
                    <strong>{candidate.title}</strong>
                    <span>
                      {candidate.source} - {candidate.visualization} - {candidate.pinnedAt}
                    </span>
                  </article>
                ))}
              </div>
            )}
          </section>
        </section>
      </section>
    </div>
  );
}
