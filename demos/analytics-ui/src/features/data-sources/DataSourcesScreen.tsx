import { Cloud, Database, FileUp, HardDrive, TableProperties } from "lucide-react";
import { useState } from "react";
import type { AppSnapshot, UploadResult } from "../../api/analyticsApi";
import { uploadDataset } from "../../api/analyticsApi";
import { fileName, formatValue } from "../../lib/format";

type DataSourcesScreenProps = {
  snapshot: AppSnapshot;
  onUploaded: (result: UploadResult) => void;
  onRefresh: () => Promise<void>;
};

const connectorCards = [
  {
    name: "CSV Upload",
    status: "Available",
    description: "Import a local CSV into SQLite and Tablesaw.",
    icon: FileUp
  },
  {
    name: "SQLite",
    status: "Local",
    description: "Use the generated SQLite session database.",
    icon: Database
  },
  {
    name: "Hugging Face",
    status: "Metadata",
    description: "Dataset references and scale planning without CI downloads.",
    icon: Cloud
  },
  {
    name: "Kaggle",
    status: "Metadata",
    description: "Dataset cards and manual-download workflow.",
    icon: TableProperties
  },
  {
    name: "BigQuery Public",
    status: "Planned",
    description: "Pushdown SQL, cost estimates, and bounded fetches.",
    icon: HardDrive
  }
];

export function DataSourcesScreen({ snapshot, onUploaded, onRefresh }: DataSourcesScreenProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [status, setStatus] = useState("Ready");
  const [uploading, setUploading] = useState(false);

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
        {connectorCards.map((connector) => {
          const Icon = connector.icon;
          return (
            <article className="connector-card" key={connector.name}>
              <div className="connector-icon">
                <Icon size={18} />
              </div>
              <div>
                <div className="section-heading compact">
                  <h3>{connector.name}</h3>
                  <span className="pill">{connector.status}</span>
                </div>
                <p className="muted">{connector.description}</p>
              </div>
            </article>
          );
        })}
      </section>
    </div>
  );
}
