import { useTranslation } from "react-i18next";
import {
  downloadJournalExport,
  type JournalExportFormat,
  type JournalExportRow,
} from "../../utils/journalExport";

interface JournalExportButtonsProps {
  filenameBase: string;
  rows: JournalExportRow[];
  disabled?: boolean;
}

export default function JournalExportButtons({
  filenameBase,
  rows,
  disabled = false,
}: JournalExportButtonsProps) {
  const { t } = useTranslation("journal");

  if (rows.length === 0) {
    return null;
  }

  const exportRows = (format: JournalExportFormat) => {
    downloadJournalExport(filenameBase, format, rows);
  };

  return (
    <div className="journal-export-actions" role="group" aria-label={t("export.label")}>
      <button
        type="button"
        className="btn small"
        disabled={disabled}
        onClick={() => exportRows("csv")}
      >
        {t("export.csv")}
      </button>
      <button
        type="button"
        className="btn small"
        disabled={disabled}
        onClick={() => exportRows("json")}
      >
        {t("export.json")}
      </button>
    </div>
  );
}
