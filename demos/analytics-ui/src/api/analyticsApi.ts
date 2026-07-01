export type SessionInfo = {
  csv: string;
  database: string;
  dashboardSpec: string;
  dashboardHtml: string;
  rows: number;
};

export type DashboardTile = {
  id: string;
  type: "kpi" | "chart" | "table" | "insight" | string;
  title: string;
  value?: unknown;
  subtitle?: string;
  body?: string;
  chart?: {
    data?: {
      values?: Array<Record<string, unknown>>;
    };
  };
  data?: {
    rows?: Array<Record<string, unknown>>;
  };
};

export type DashboardModel = {
  version: number;
  revision?: number;
  title: string;
  generatedAt?: string;
  updatedAt?: string;
  source?: Record<string, unknown>;
  filters?: Array<Record<string, unknown>>;
  tiles?: DashboardTile[];
  layout?: Array<Record<string, unknown>>;
  lastFilterResult?: Record<string, unknown>;
};

export type FilterOptions = {
  filters: Array<{
    id: string;
    label: string;
    type: "multi_select" | "range" | string;
    field: string;
    values?: string[];
    range?: {
      min: number;
      max: number;
    };
  }>;
};

export type AppSnapshot = {
  session: SessionInfo | null;
  dashboard: DashboardModel | null;
  filterOptions: FilterOptions | null;
};

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path);
  if (!response.ok) {
    throw new Error(`${path} returned ${response.status}`);
  }
  return (await response.json()) as T;
}

export async function loadAppSnapshot(): Promise<AppSnapshot> {
  const [session, dashboard, filterOptions] = await Promise.all([
    getJson<SessionInfo>("/api/session"),
    getJson<DashboardModel>("/api/dashboard"),
    getJson<FilterOptions>("/api/filter-options")
  ]);
  return { session, dashboard, filterOptions };
}
