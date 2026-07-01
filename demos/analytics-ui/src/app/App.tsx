import { useEffect, useMemo, useState } from "react";
import {
  Activity,
  BarChart3,
  Bot,
  Database,
  FileUp,
  LayoutDashboard,
  RefreshCw,
  ShieldCheck
} from "lucide-react";
import { loadAppSnapshot, type AppSnapshot, type DashboardTile, type UploadResult } from "../api/analyticsApi";
import { AppShell, type NavItem } from "../components/AppShell";
import { ResultTable } from "../components/ResultTable";
import { AskDataScreen } from "../features/ask-data/AskDataScreen";
import { DataPreviewScreen } from "../features/data-preview/DataPreviewScreen";
import { DataSourcesScreen } from "../features/data-sources/DataSourcesScreen";
import { fileName, formatValue } from "../lib/format";

const navItems: NavItem[] = [
  { id: "sources", label: "Data Sources", icon: FileUp },
  { id: "preview", label: "Data Preview", icon: Database },
  { id: "ask", label: "Ask Data", icon: Bot },
  { id: "analysis", label: "Analysis", icon: Activity },
  { id: "charts", label: "Charts", icon: BarChart3 },
  { id: "dashboard", label: "Dashboard", icon: LayoutDashboard },
  { id: "evidence", label: "Evidence", icon: ShieldCheck }
];

function kpiTiles(snapshot: AppSnapshot): DashboardTile[] {
  return snapshot.dashboard?.tiles?.filter((tile) => tile.type === "kpi") ?? [];
}

function chartTile(snapshot: AppSnapshot): DashboardTile | undefined {
  return snapshot.dashboard?.tiles?.find((tile) => tile.id === "chart-revenue-channel");
}

function tableTile(snapshot: AppSnapshot): DashboardTile | undefined {
  return snapshot.dashboard?.tiles?.find((tile) => tile.id === "table-channel");
}

function metricValue(row: Record<string, unknown>, preferred: string[]): number {
  for (const key of preferred) {
    const value = row[key];
    if (typeof value === "number") {
      return value;
    }
  }
  const discovered = Object.entries(row).find(([key, value]) => {
    const normalized = key.toLowerCase();
    return typeof value === "number" && preferred.some((name) => normalized.includes(name));
  });
  return typeof discovered?.[1] === "number" ? discovered[1] : 0;
}

function AppContent({ snapshot }: { snapshot: AppSnapshot }) {
  const chartRows = chartTile(snapshot)?.chart?.data?.values ?? [];
  const tableRows = tableTile(snapshot)?.data?.rows ?? [];
  const filterCount = snapshot.filterOptions?.filters?.length ?? 0;

  return (
    <div className="workspace-grid">
      <section className="panel hero-panel">
        <div>
          <p className="eyebrow">Active Dataset</p>
          <h2>{fileName(snapshot.session?.csv)}</h2>
          <p className="muted">
            {formatValue(snapshot.session?.rows)} rows loaded through the Java analytics backend.
          </p>
        </div>
        <div className="hero-stats">
          <div>
            <span>{filterCount}</span>
            <small>generated filters</small>
          </div>
          <div>
            <span>{snapshot.dashboard?.tiles?.length ?? 0}</span>
            <small>dashboard tiles</small>
          </div>
        </div>
      </section>

      <section className="metric-row">
        {kpiTiles(snapshot).map((tile) => (
          <article className="metric-card" key={tile.id}>
            <p>{tile.title}</p>
            <strong>{formatValue(tile.value)}</strong>
            <span>{tile.subtitle}</span>
          </article>
        ))}
      </section>

      <section className="panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Dashboard Preview</p>
            <h3>Revenue By Channel</h3>
          </div>
          <span className="pill">{chartRows.length} rows</span>
        </div>
        <div className="bar-list">
          {chartRows.map((row) => {
            const channel = String(row.channel ?? "unknown");
            const revenue = metricValue(row, ["revenue", "mean"]);
            const max = Math.max(...chartRows.map((item) => metricValue(item, ["revenue", "mean"])), 1);
            return (
              <div className="bar-row" key={channel}>
                <span>{channel}</span>
                <div className="bar-track">
                  <div className="bar-fill" style={{ width: `${Math.max(4, (revenue / max) * 100)}%` }} />
                </div>
                <strong>{formatValue(revenue)}</strong>
              </div>
            );
          })}
        </div>
      </section>

      <section className="panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Current Rollup</p>
            <h3>Channel Table</h3>
          </div>
          <span className="pill">{tableRows.length} rows</span>
        </div>
        <ResultTable rows={tableRows} />
      </section>
    </div>
  );
}

function Placeholder({ title, body }: { title: string; body: string }) {
  return (
    <section className="panel placeholder-panel">
      <p className="eyebrow">Milestone A scaffold</p>
      <h2>{title}</h2>
      <p className="muted">{body}</p>
    </section>
  );
}

export function App() {
  const [active, setActive] = useState("dashboard");
  const [snapshot, setSnapshot] = useState<AppSnapshot>({ session: null, dashboard: null, filterOptions: null });
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  async function refresh() {
    setLoading(true);
    setError(null);
    try {
      setSnapshot(await loadAppSnapshot());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to load analytics app state");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, []);

  function handleUploaded(result: UploadResult) {
    setSnapshot((current) => ({
      ...current,
      dashboard: result.dashboard,
      filterOptions: result.filterOptions,
      session: current.session ? { ...current.session, csv: result.filename, rows: result.rows } : current.session
    }));
    setActive("preview");
  }

  const content = useMemo(() => {
    if (loading) {
      return <Placeholder title="Loading analytics workspace" body="Connecting to the Java analytics API." />;
    }
    if (error) {
      return <Placeholder title="Backend not reachable" body={error} />;
    }
    if (active === "sources") {
      return <DataSourcesScreen onRefresh={refresh} onUploaded={handleUploaded} snapshot={snapshot} />;
    }
    if (active === "preview") {
      return <DataPreviewScreen snapshot={snapshot} />;
    }
    if (active === "ask") {
      return <AskDataScreen snapshot={snapshot} />;
    }
    if (active === "dashboard") {
      return <AppContent snapshot={snapshot} />;
    }
    const selected = navItems.find((item) => item.id === active);
    return (
      <Placeholder
        title={selected?.label ?? "Analytics Workspace"}
        body="This route is scaffolded for the next milestones and will be wired to the same analytics API."
      />
    );
  }, [active, error, loading, snapshot]);

  return (
    <AppShell
      active={active}
      navItems={navItems}
      onSelect={setActive}
      title="Analytics Workbench"
      subtitle="Local-first analytics app over CSV, SQLite, Tablesaw, dashboards, and Ollama summaries."
      actions={
        <button className="icon-button" onClick={() => void refresh()} type="button">
          <RefreshCw size={16} />
          Refresh
        </button>
      }
    >
      {content}
    </AppShell>
  );
}
