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
import { loadAppSnapshot, type AppSnapshot, type UploadResult } from "../api/analyticsApi";
import { AppShell, type NavItem } from "../components/AppShell";
import { AnalysisWorkspaceScreen } from "../features/analysis-workspace/AnalysisWorkspaceScreen";
import { AskDataScreen } from "../features/ask-data/AskDataScreen";
import { DataPreviewScreen } from "../features/data-preview/DataPreviewScreen";
import { DataSourcesScreen } from "../features/data-sources/DataSourcesScreen";
import { DashboardBuilderScreen } from "../features/dashboard-builder/DashboardBuilderScreen";

const navItems: NavItem[] = [
  { id: "sources", label: "Data Sources", icon: FileUp },
  { id: "preview", label: "Data Preview", icon: Database },
  { id: "ask", label: "Ask Data", icon: Bot },
  { id: "analysis", label: "Analysis", icon: Activity },
  { id: "charts", label: "Charts", icon: BarChart3 },
  { id: "dashboard", label: "Dashboard", icon: LayoutDashboard },
  { id: "evidence", label: "Evidence", icon: ShieldCheck }
];

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
    if (active === "analysis") {
      return <AnalysisWorkspaceScreen snapshot={snapshot} />;
    }
    if (active === "dashboard") {
      return (
        <DashboardBuilderScreen
          onDashboardChanged={(dashboard) => setSnapshot((current) => ({ ...current, dashboard }))}
          snapshot={snapshot}
        />
      );
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
