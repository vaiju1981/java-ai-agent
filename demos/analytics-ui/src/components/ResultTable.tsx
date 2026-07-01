import { formatValue } from "../lib/format";

type ResultTableProps = {
  rows: Array<Record<string, unknown>>;
  emptyText?: string;
};

export function ResultTable({ rows, emptyText = "No rows available" }: ResultTableProps) {
  if (rows.length === 0) {
    return <div className="empty-state">{emptyText}</div>;
  }

  const columns = Object.keys(rows[0]);

  return (
    <div className="table-shell">
      <table>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column}>{column}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              {columns.map((column) => (
                <td key={column}>{formatValue(row[column])}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
