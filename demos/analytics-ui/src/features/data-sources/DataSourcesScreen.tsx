import { Cloud, Database, FileUp, HardDrive, Play, TableProperties } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type { AppSnapshot, ConnectorCatalog, ConnectorInfo, ConnectorPlan, UploadResult } from "../../api/analyticsApi";
import { loadConnectorCatalog, planConnectorScale, uploadDataset } from "../../api/analyticsApi";
import { ResultTable } from "../../components/ResultTable";
import { fileName, formatValue } from "../../lib/format";

type DataSourcesScreenProps = {
  snapshot: AppSnapshot;
  onUploaded: (result: UploadResult) => void;
  onRefresh: () => Promise<void>;
};

const connectorIcons = {
  "csv-upload": FileUp,
  sqlite: Database,
  huggingface: Cloud,
  kaggle: TableProperties,
  "bigquery-public": HardDrive
};

function connectorIcon(connector: ConnectorInfo) {
  return connectorIcons[connector.id as keyof typeof connectorIcons] ?? Database;
}

function datasetRows(connector: ConnectorInfo) {
  return connector.datasets.map((dataset) => ({
    id: dataset.id,
    name: dataset.name,
    rows: dataset.estimatedRows,
    storage: dataset.storage
  }));
}

function planRows(plan: ConnectorPlan) {
  return [
    { metric: "mode", value: plan.plan.mode },
    { metric: "sampleRows", value: plan.plan.sampleRows },
    { metric: "maxFetchRows", value: plan.plan.maxFetchRows },
    { metric: "estimatedRows", value: plan.plan.estimatedRows },
    { metric: "estimatedBytes", value: plan.plan.estimatedBytes },
    { metric: "estimatedCostUsd", value: plan.plan.estimatedCostUsd },
    { metric: "rawDataSentToModel", value: plan.rawDataSentToModel ? "yes" : "no" }
  ];
}

export function DataSourcesScreen({ snapshot, onUploaded, onRefresh }: DataSourcesScreenProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [status, setStatus] = useState("Ready");
  const [uploading, setUploading] = useState(false);
  const [catalog, setCatalog] = useState<ConnectorCatalog | null>(null);
  const [selectedConnectorId, setSelectedConnectorId] = useState("sqlite");
  const [selectedDatasetId, setSelectedDatasetId] = useState("");
  const [sampleRows, setSampleRows] = useState(1000);
  const [plan, setPlan] = useState<ConnectorPlan | null>(null);
  const [planning, setPlanning] = useState(false);

  useEffect(() => {
    let cancelled = false;
    loadConnectorCatalog()
      .then((next) => {
        if (!cancelled) {
          setCatalog(next);
          const preferred = next.connectors.find((connector) => connector.id === selectedConnectorId) ?? next.connectors[0];
          setSelectedConnectorId(preferred?.id ?? "sqlite");
          setSelectedDatasetId(preferred?.datasets[0]?.id ?? "");
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setStatus(error instanceof Error ? error.message : "Connector catalog unavailable.");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [snapshot.session?.csv]);

  const selectedConnector = useMemo(
    () => catalog?.connectors.find((connector) => connector.id === selectedConnectorId) ?? null,
    [catalog, selectedConnectorId]
  );

  async function handleUpload() {
    if (!selectedFile) {
      setStatus("Choose a CSV file first.");
      return;
    }
    setUploading(true);
    setStatus(`Uploading ${selectedFile.name}`);
    try {
      const result = await uploadDataset(selectedFile);
      onUploaded(result);
      await onRefresh();
      setStatus(`${result.filename} loaded with ${formatValue(result.rows)} rows.`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Upload failed.");
    } finally {
      setUploading(false);
    }
  }

  async function handlePlan() {
    if (!selectedConnector || !selectedDatasetId) {
      setStatus("Choose a connector dataset first.");
      return;
    }
    setPlanning(true);
    setStatus(`Planning ${selectedConnector.name}`);
    try {
      const next = await planConnectorScale({
        connectorId: selectedConnector.id,
        datasetId: selectedDatasetId,
        sampleRows
      });
      setPlan(next);
      setStatus(`${selectedConnector.name} scale plan ready.`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Scale plan failed.");
    } finally {
      setPlanning(false);
    }
  }

  return (
    <div className="workspace-grid">
      <section className="panel hero-panel">
        <div>
          <p className="eyebrow">Active Session</p>
          <h2>{fileName(snapshot.session?.csv)}</h2>
          <p className="muted">
            {formatValue(snapshot.session?.rows)} rows, {snapshot.filterOptions?.filters.length ?? 0} generated filters.
          </p>
        </div>
        <div className="hero-stats">
          <div>
            <span>{snapshot.dashboard?.tiles?.length ?? 0}</span>
            <small>tiles</small>
          </div>
          <div>
            <span>{snapshot.filterOptions?.filters.length ?? 0}</span>
            <small>filters</small>
          </div>
        </div>
      </section>

      <section className="panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Upload</p>
            <h3>Bring Your CSV</h3>
          </div>
          <span className="pill">{status}</span>
        </div>
        <div className="upload-row">
          <input accept=".csv,text/csv" onChange={(event) => setSelectedFile(event.target.files?.[0] ?? null)} type="file" />
          <button className="primary-button" disabled={uploading} onClick={() => void handleUpload()} type="button">
            <FileUp size={16} />
            Upload Dataset
          </button>
        </div>
      </section>

      <section className="connector-grid">
        {(catalog?.connectors ?? []).map((connector) => {
          const Icon = connectorIcon(connector);
          return (
            <article
              className={`connector-card ${selectedConnectorId === connector.id ? "selected" : ""}`}
              key={connector.id}
            >
              <div className="connector-icon">
                <Icon size={18} />
              </div>
              <div>
                <div className="section-heading compact">
                  <h3>{connector.name}</h3>
                  <span className="pill">{connector.status}</span>
                </div>
                <p className="muted">{connector.description}</p>
                <div className="connector-meta">
                  <span>{connector.mode}</span>
                  <span>{connector.requiresCredentials ? "credentials required" : "no credentials"}</span>
                  <span>{connector.capabilities.join(", ")}</span>
                </div>
                <button
                  className="icon-button"
                  onClick={() => {
                    setSelectedConnectorId(connector.id);
                    setSelectedDatasetId(connector.datasets[0]?.id ?? "");
                    setPlan(null);
                  }}
                  type="button"
                >
                  Select
                </button>
              </div>
            </article>
          );
        })}
      </section>

      <section className="panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Scale Plan</p>
            <h3>{selectedConnector?.name ?? "Connector"}</h3>
          </div>
          <span className="pill">{catalog?.policy.rawDataSentToModel === false ? "no raw model context" : "review"}</span>
        </div>
        <div className="scale-controls">
          <label className="control-field">
            <span>Dataset</span>
            <select onChange={(event) => setSelectedDatasetId(event.target.value)} value={selectedDatasetId}>
              {(selectedConnector?.datasets ?? []).map((dataset) => (
                <option key={dataset.id} value={dataset.id}>
                  {dataset.name}
                </option>
              ))}
            </select>
          </label>
          <label className="control-field">
            <span>Sample Rows</span>
            <input
              max={catalog?.policy.maxFetchRows ?? 25000}
              min={1}
              onChange={(event) => setSampleRows(Number(event.target.value))}
              type="number"
              value={sampleRows}
            />
          </label>
          <button className="primary-button" disabled={planning || !selectedConnector} onClick={() => void handlePlan()} type="button">
            <Play size={16} />
            Plan
          </button>
        </div>
        {selectedConnector && <ResultTable rows={datasetRows(selectedConnector)} />}
      </section>

      {plan && (
        <section className="panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Execution Guardrails</p>
              <h3>{plan.datasetId}</h3>
            </div>
            <span className="pill">{plan.cache.status}</span>
          </div>
          <ResultTable rows={planRows(plan)} />
          <pre className="json-block">{plan.plan.pushdownSql}</pre>
          <div className="filter-chip-row">
            {plan.warnings.map((warning) => (
              <div className="filter-chip" key={warning}>
                <strong>Guardrail</strong>
                <span>{warning}</span>
              </div>
            ))}
          </div>
        </section>
      )}

      <section className="panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Result Cache</p>
            <h3>Local And Metadata Artifacts</h3>
          </div>
          <span className="pill">{catalog?.cache.length ?? 0} entries</span>
        </div>
        <ResultTable rows={catalog?.cache ?? []} />
      </section>
    </div>
  );
}
