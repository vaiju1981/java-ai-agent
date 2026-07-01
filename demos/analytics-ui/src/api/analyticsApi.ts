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

export type SchemaColumn = {
  name: string;
  sqlName: string;
  type: "numeric" | "categorical" | string;
};

export type SchemaSummary = {
  table: string;
  rows: number;
  columns: SchemaColumn[];
};

export type ProfileColumn = {
  name: string;
  type: string;
  missing: number;
  min?: number;
  max?: number;
  mean?: number;
  distinct?: number;
  top?: {
    value: string;
    count: number;
  };
};

export type DatasetProfile = {
  table?: string;
  rows: number;
  columns: ProfileColumn[];
};

export type QualityColumn = {
  column: string;
  missing: number;
  missingRate: number;
  distinct?: number;
};

export type DataQuality = {
  rows: number;
  duplicateRowGroups: number;
  columns: QualityColumn[];
};

export type SampleRows = {
  rows: Array<Record<string, unknown>>;
  limitedTo: number;
};

export type DatasetPreview = {
  schema: SchemaSummary;
  profile: DatasetProfile;
  quality: DataQuality;
  sample: SampleRows;
};

export type UploadResult = {
  status: string;
  filename: string;
  rows: number;
  filterOptions: FilterOptions;
  dashboard: DashboardModel;
};

export type ChatResponse = {
  question: string;
  answer: string;
  model?: string;
  tool?: string;
  result?: unknown;
  error?: string;
};

export type AnalysisKind =
  | "profile"
  | "correlations"
  | "features"
  | "segments"
  | "regression"
  | "anomalies"
  | "forecast"
  | "causal-check";

export type AnalysisRequest = {
  target?: string;
  segment?: string;
  treatment?: string;
  outcome?: string;
  timeColumn?: string;
  periods?: number;
  features?: string[];
};

export type AnalysisResponse = {
  analysis: AnalysisKind | string;
  title: string;
  description: string;
  visualization: string;
  result: unknown;
  tileCandidate: {
    id: string;
    type: string;
    title: string;
    source: string;
    visualization: string;
    result: unknown;
  };
};

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path);
  if (!response.ok) {
    throw new Error(`${path} returned ${response.status}`);
  }
  return (await response.json()) as T;
}

async function postJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  const payload = (await response.json()) as T & { error?: string };
  if (!response.ok || payload.error) {
    throw new Error(payload.error ?? `${path} returned ${response.status}`);
  }
  return payload;
}

export async function loadAppSnapshot(): Promise<AppSnapshot> {
  const [session, dashboard, filterOptions] = await Promise.all([
    getJson<SessionInfo>("/api/session"),
    getJson<DashboardModel>("/api/dashboard"),
    getJson<FilterOptions>("/api/filter-options")
  ]);
  return { session, dashboard, filterOptions };
}

export async function loadDatasetPreview(): Promise<DatasetPreview> {
  const [schema, profile, quality, sample] = await Promise.all([
    getJson<SchemaSummary>("/api/schema"),
    getJson<DatasetProfile>("/api/profile"),
    getJson<DataQuality>("/api/quality"),
    getJson<SampleRows>("/api/sample")
  ]);
  return { schema, profile, quality, sample };
}

export async function loadSchemaSummary(): Promise<SchemaSummary> {
  return getJson<SchemaSummary>("/api/schema");
}

export async function uploadDataset(file: File): Promise<UploadResult> {
  return postJson<UploadResult>("/api/upload", {
    filename: file.name,
    content: await file.text()
  });
}

export async function askData(question: string): Promise<ChatResponse> {
  const response = await fetch("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question })
  });
  const payload = (await response.json()) as ChatResponse;
  if (!response.ok) {
    throw new Error(payload.error ?? `/api/chat returned ${response.status}`);
  }
  return payload;
}

export async function runAnalysis(kind: AnalysisKind, request: AnalysisRequest): Promise<AnalysisResponse> {
  return postJson<AnalysisResponse>(`/api/analysis/${kind}`, request);
}
