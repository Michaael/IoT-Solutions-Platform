interface BffDataTableProps {
  rows: Array<Record<string, unknown>>;
  labels?: Record<string, string>;
  emptyMessage?: string;
}

export default function BffDataTable({ rows, labels, emptyMessage }: BffDataTableProps) {
  if (rows.length === 0) {
    return <p className="op-muted">{emptyMessage ?? "Нет данных."}</p>;
  }

  const columns = Object.keys(rows[0]);

  return (
    <div className="op-table-wrap">
      <table className="op-table">
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column}>{labels?.[column] ?? column}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={index}>
              {columns.map((column) => (
                <td key={column}>{formatCell(row[column])}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function formatCell(value: unknown): string {
  if (value === null || value === undefined) {
    return "—";
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}
