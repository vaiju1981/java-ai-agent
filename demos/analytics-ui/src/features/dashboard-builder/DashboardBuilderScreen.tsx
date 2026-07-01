import { Download, Filter, Pin, RotateCcw, Save } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type {
  AppSnapshot,
  DashboardFilterResult,
  DashboardFilters,
  DashboardModel,
  DashboardTile
} from "../../api/analyticsApi";
import {
  applyDashboardFilters,
  exportDashboard,
  pinDashboardTile,
  reloadDashboard,
  saveDashboard
} from "../../api/analyticsApi";
import { ResultTable } from "../../components/ResultTable";
import { fileName, formatValue } from "../../lib/format";

type DashboardBuilderScreenProps = {
  snapshot: AppSnapshot;
  onDashboardChanged: (dashboard: DashboardModel) => void;
};

type FilterState = {
  channel: string[];
  week: {
    from: number;
    to: number;
  };
};

function initialFilters(snapshot: AppSnapshot): FilterState {
  const channel = snapshot.filterOptions?.filters.find((filter) => filter.id === "channel");
  const week = snapshot.filterOptions?.filters.find((filter) => filter.id === "week");
  return {
    channel: channel?.values ?? [],
    week: {
      from: Math.floor(week?.range?.min ?? 1),
      to: Math.ceil(week?.range?.max ?? 16)
    }
  };
}

function tileById(dashboard: DashboardModel | null, id: string): DashboardTile | undefined {
  return dashboard?.tiles?.find((tile) => tile.id === id);
}

function filteredRows(filterResult: DashboardFilterResult | null, tileId: string): Array<Record<string, unknown>> | null {
  return filterResult?.result.tiles?.find((tile) => tile.tileId === tileId)?.data?.rows ?? null;
}

function firstFilteredRow(filterResult: DashboardFilterResult | null, tileId: string): Record<string, unknown> | null {
  return filteredRows(filterResult, tileId)?.[0] ?? null;
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

function downloadText(filename: string, text: string, type: string) {
  const blob = new Blob([text], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function BarChart({
  rows,
  onDrilldown
}: {
  rows: Array<Record<string, unknown>>;
  onDrilldown: (channel: string) => void;
}) {
  const max = Math.max(...rows.map((row) => metricValue(row, ["revenue", "avg_revenue", "mean"])), 1);

  if (rows.length === 0) {
    return <div className="empty-state">No chart rows available.</div>;
  }

  return (
    <div className="bar-list">
      {rows.map((row) => {
        const channel = String(row.channel ?? "unknown");
        const revenue = metricValue(row, ["revenue", "avg_revenue", "mean"]);
        return (
          <button className="drilldown-bar" key={channel} onClick={() => onDrilldown(channel)} type="button">
            <span>{channel}</span>
            <div className="bar-track">
              <div className="bar-fill" style={{ width: `${Math.max(4, (revenue / max) * 100)}%` }} />
            </div>
            <strong>{formatValue(revenue)}</strong>
          </button>
        );
      })}
    </div>
  );
}

export function DashboardBuilderScreen({ onDashboardChanged, snapshot }: DashboardBuilderScreenProps) {
  const [dashboard, setDashboard] = useState<DashboardModel | null>(snapshot.dashboard);
  const [filters, setFilters] = useState<FilterState>(() => initialFilters(snapshot));
  const [filterResult, setFilterResult] = useState<DashboardFilterResult | null>(null);
  const [status, setStatus] = useState("Ready");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setDashboard(snapshot.dashboard);
    setFilters(initialFilters(snapshot));
    setFilterResult(null);
    setStatus("Ready");
  }, [snapshot.dashboard, snapshot.filterOptions, snapshot.session?.csv]);

  const chartRows =
    filteredRows(filterResult, "chart-revenue-channel") ??
    tileById(dashboard, "chart-revenue-channel")?.chart?.data?.values ??
    [];
  const tableRows = filteredRows(filterResult, "table-channel") ?? tileById(dashboard, "table-channel")?.data?.rows ?? [];
  const drilldownRows = filterResult?.result.drilldown?.data?.rows ?? [];
  const pinnedTiles = useMemo(
    () => dashboard?.tiles?.filter((tile) => tile.id.startsWith("pinned-") || tile.evidenceIds !== undefined) ?? [],
    [dashboard]
  );

  function currentFilters(): DashboardFilters {
    return {
      channel: filters.channel,
      week: filters.week
    };
  }

  function updateDashboard(next: DashboardModel | undefined) {
    if (next) {
      setDashboard(next);
      onDashboardChanged(next);
    }
  }

  async function applyFilters(channel = "") {
    setBusy(true);
    setStatus(channel ? `Drilling into ${channel}` : "Applying filters");
    try {
      const result = await applyDashboardFilters(
        currentFilters(),
        channel ? "chart-revenue-channel" : "",
        channel
      );
      setFilterResult(result);
      setStatus(channel ? `Drilldown loaded for ${channel}` : "Filters applied");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Dashboard filter failed");
    } finally {
      setBusy(false);
    }
  }

  async function pinCurrentView() {
    setBusy(true);
    setStatus("Pinning dashboard view");
    try {
      const result = await pinDashboardTile({
        id: "pinned-dashboard-view",
        title: "Pinned Dashboard View",
        body: `Filters: ${JSON.stringify(currentFilters())}. Rows: ${tableRows.length}. Where: ${
          filterResult?.result.where ?? "all rows"
        }.`,
        source: "dashboard-builder",
        evidenceIds: ["dashboard_filter", "dashboard_drilldown"],
        result: filterResult?.result ?? { tableRows, chartRows }
      });
      updateDashboard(result.dashboard);
      setStatus(`Pinned view at revision ${result.revision ?? "-"}`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Pin failed");
    } finally {
      setBusy(false);
    }
  }

  async function save() {
    setBusy(true);
    setStatus("Saving dashboard");
    try {
      const result = await saveDashboard();
      updateDashboard(result.dashboard);
      setStatus(`Saved ${result.dashboardSpec ?? "dashboard spec"}`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Save failed");
    } finally {
      setBusy(false);
    }
  }

  async function reload() {
    setBusy(true);
    setStatus("Reloading dashboard");
    try {
      const result = await reloadDashboard();
      updateDashboard(result.dashboard);
      setStatus("Dashboard reloaded");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Reload failed");
    } finally {
      setBusy(false);
    }
  }

  async function exportFile(format: "json" | "html") {
    setBusy(true);
    setStatus(`Exporting ${format.toUpperCase()}`);
    try {
      const text = await exportDashboard(format);
      downloadText(
        `analytics-dashboard.${format}`,
        text,
        format === "json" ? "application/json;charset=utf-8" : "text/html;charset=utf-8"
      );
      setStatus(`${format.toUpperCase()} export prepared`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Export failed");
    } finally {
      setBusy(false);
    }
  }

  function toggleChannel(channel: string) {
    setFilters((current) => ({
      ...current,
      channel: current.channel.includes(channel)
        ? current.channel.filter((item) => item !== channel)
        : [...current.channel, channel]
    }));
  }

  const channelFilter = snapshot.filterOptions?.filters.find((filter) => filter.id === "channel");
  const weekFilter = snapshot.filterOptions?.filters.find((filter) => filter.id === "week");
  const rowKpi = firstFilteredRow(filterResult, "kpi-rows");
  const revenueKpi = firstFilteredRow(filterResult, "kpi-revenue");
  const conversionKpi = firstFilteredRow(filterResult, "kpi-conversion");

  return (
    <div className="dashboard-layout">
      <section className="panel hero-panel">
        <div>
          <p className="eyebrow">Dashboard Builder</p>
          <h2>{dashboard?.title ?? fileName(snapshot.session?.csv)}</h2>
          <p className="muted">
            {fileName(snapshot.session?.csv)} with {formatValue(snapshot.session?.rows)} active rows.
          </p>
        </div>
        <div className="hero-stats">
          <div>
            <span>{dashboard?.tiles?.length ?? 0}</span>
            <small>tiles</small>
          </div>
          <div>
            <span>{pinnedTiles.length}</span>
            <small>pinned</small>
          </div>
        </div>
      </section>

      <section className="dashboard-shell">
        <aside className="panel dashboard-controls">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Filters</p>
              <h3>Generated Controls</h3>
            </div>
            <Filter size={18} />
          </div>

          <div className="filter-stack">
            <strong>Channel</strong>
            <div className="checkbox-stack">
              {(channelFilter?.values ?? []).map((channel) => (
                <label className="checkbox-row" key={channel}>
                  <input
                    checked={filters.channel.includes(channel)}
                    onChange={() => toggleChannel(channel)}
                    type="checkbox"
                  />
                  <span>{channel}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="filter-stack">
            <strong>Week Range</strong>
            <div className="range-row">
              <input
                max={weekFilter?.range?.max}
                min={weekFilter?.range?.min}
                onChange={(event) =>
                  setFilters((current) => ({
                    ...current,
                    week: { ...current.week, from: Number(event.target.value) }
                  }))
                }
                type="number"
                value={filters.week.from}
              />
              <input
                max={weekFilter?.range?.max}
                min={weekFilter?.range?.min}
                onChange={(event) =>
                  setFilters((current) => ({
                    ...current,
                    week: { ...current.week, to: Number(event.target.value) }
                  }))
                }
                type="number"
                value={filters.week.to}
              />
            </div>
          </div>

          <div className="dashboard-action-grid">
            <button className="primary-button" disabled={busy} onClick={() => void applyFilters()} type="button">
              <Filter size={16} />
              Apply
            </button>
            <button className="icon-button" disabled={busy} onClick={() => void pinCurrentView()} type="button">
              <Pin size={16} />
              Pin
            </button>
            <button className="icon-button" disabled={busy} onClick={() => void save()} type="button">
              <Save size={16} />
              Save
            </button>
            <button className="icon-button" disabled={busy} onClick={() => void reload()} type="button">
              <RotateCcw size={16} />
              Reload
            </button>
          </div>

          <div className="dashboard-action-grid">
            <button className="icon-button" disabled={busy} onClick={() => void exportFile("json")} type="button">
              <Download size={16} />
              JSON
            </button>
            <button className="icon-button" disabled={busy} onClick={() => void exportFile("html")} type="button">
              <Download size={16} />
              HTML
            </button>
          </div>

          <div className="status-panel">{status}</div>
        </aside>

        <section className="workspace-grid">
          <section className="metric-row">
            <article className="metric-card">
              <p>Rows</p>
              <strong>{formatValue(rowKpi?.rows ?? tileById(dashboard, "kpi-rows")?.value)}</strong>
              <span>{filterResult?.result.where ?? "all rows"}</span>
            </article>
            <article className="metric-card">
              <p>Total Revenue</p>
              <strong>{formatValue(revenueKpi?.total_revenue ?? tileById(dashboard, "kpi-revenue")?.value)}</strong>
              <span>Filtered sum</span>
            </article>
            <article className="metric-card">
              <p>Avg Conversion</p>
              <strong>
                {formatValue(conversionKpi?.avg_conversion_rate ?? tileById(dashboard, "kpi-conversion")?.value)}
              </strong>
              <span>Filtered average</span>
            </article>
          </section>

          <section className="panel">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Chart</p>
                <h3>Revenue By Channel</h3>
              </div>
              <span className="pill">{chartRows.length} rows</span>
            </div>
            <BarChart rows={chartRows} onDrilldown={(channel) => void applyFilters(channel)} />
          </section>

          <section className="panel">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Table</p>
                <h3>Channel Rollup</h3>
              </div>
              <span className="pill">{tableRows.length} rows</span>
            </div>
            <ResultTable rows={tableRows} />
          </section>

          <section className="panel">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Drilldown</p>
                <h3>{filterResult?.result.drilldown?.value ?? "Select A Channel"}</h3>
              </div>
              <span className="pill">{drilldownRows.length} rows</span>
            </div>
            <ResultTable rows={drilldownRows} emptyText="Click a channel in the chart to inspect supporting rows." />
          </section>

          <section className="panel">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Pinned Tiles</p>
                <h3>Saved In Dashboard Artifact</h3>
              </div>
              <span className="pill">{pinnedTiles.length} pinned</span>
            </div>
            {pinnedTiles.length === 0 ? (
              <div className="empty-state">No pinned dashboard tiles yet.</div>
            ) : (
              <div className="candidate-list">
                {pinnedTiles.map((tile) => (
                  <article className="candidate-item" key={tile.id}>
                    <strong>{tile.title}</strong>
                    <span>{tile.body ?? tile.id}</span>
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
