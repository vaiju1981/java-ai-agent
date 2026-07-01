import { useEffect, useState } from "react";
import type { AppSnapshot, DatasetPreview } from "../../api/analyticsApi";
import { loadDatasetPreview } from "../../api/analyticsApi";
import { ResultTable } from "../../components/ResultTable";
import { formatValue } from "../../lib/format";

type DataPreviewScreenProps = {
  snapshot: AppSnapshot;
};

function qualityRows(preview: DatasetPreview) {
  return preview.quality.columns.map((column) => ({
    column: column.column,
    missing: column.missing,
    missingRate: column.missingRate,
    distinct: column.distinct ?? "-"
  }));
}

function schemaRows(preview: DatasetPreview) {
  return preview.schema.columns.map((column) => {
    const profile = preview.profile.columns.find((entry) => entry.name === column.name);
    return {
      name: column.name,
      type: column.type,
      missing: profile?.missing ?? 0,
      distinct: profile?.distinct ?? "-",
      min: profile?.min ?? "-",
      max: profile?.max ?? "-",
      mean: profile?.mean ?? "-"
    };
  });
}

export function DataPreviewScreen({ snapshot }: DataPreviewScreenProps) {
  const [preview, setPreview] = useState<DatasetPreview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    loadDatasetPreview()
      .then((next) => {
        if (!cancelled) {
          setPreview(next);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Unable to load preview data");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [snapshot.session?.csv]);

  if (loading) {
    return (
      <section className="panel placeholder-panel">
        <p className="eyebrow">Data Preview</p>
        <h2>Loading dataset preview</h2>
        <p className="muted">Reading schema, sample rows, and quality metrics.</p>
      </section>
    );
  }

  if (error || !preview) {
    return (
      <section className="panel placeholder-panel">
        <p className="eyebrow">Data Preview</p>
        <h2>Preview unavailable</h2>
        <p className="muted">{error ?? "No preview data returned."}</p>
      </section>
    );
  }

  return (
    <div className="workspace-grid">
      <section className="metric-row">
        <article className="metric-card">
          <p>Rows</p>
          <strong>{formatValue(preview.schema.rows)}</strong>
          <span>Imported records</span>
        </article>
        <article className="metric-card">
          <p>Columns</p>
          <strong>{formatValue(preview.schema.columns.length)}</strong>
          <span>Inferred fields</span>
        </article>
        <article className="metric-card">
          <p>Duplicate Groups</p>
          <strong>{formatValue(preview.quality.duplicateRowGroups)}</strong>
          <span>Potential duplicate row groups</span>
        </article>
      </section>

      <section className="panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Generated Filters</p>
            <h3>Active Filter Values</h3>
          </div>
          <span className="pill">{snapshot.filterOptions?.filters.length ?? 0} filters</span>
        </div>
        <div className="filter-chip-row">
          {(snapshot.filterOptions?.filters ?? []).map((filter) => (
            <div className="filter-chip" key={filter.id}>
              <strong>{filter.label}</strong>
              <span>
                {filter.values?.join(", ") ??
                  `${formatValue(filter.range?.min)} to ${formatValue(filter.range?.max)}`}
              </span>
            </div>
          ))}
        </div>
      </section>

      <section className="panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Schema</p>
            <h3>Columns And Inferred Types</h3>
          </div>
          <span className="pill">{preview.schema.columns.length} columns</span>
        </div>
        <ResultTable rows={schemaRows(preview)} />
      </section>

      <section className="panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Quality</p>
            <h3>Missingness And Cardinality</h3>
          </div>
          <span className="pill">{preview.quality.rows} rows</span>
        </div>
        <ResultTable rows={qualityRows(preview)} />
      </section>

      <section className="panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Sample</p>
            <h3>First {preview.sample.rows.length} Rows</h3>
          </div>
          <span className="pill">limited to {preview.sample.limitedTo}</span>
        </div>
        <ResultTable rows={preview.sample.rows} />
      </section>
    </div>
  );
}
