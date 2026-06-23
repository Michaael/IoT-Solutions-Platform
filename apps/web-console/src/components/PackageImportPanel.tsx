import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { importPackage } from "../api/packages";

interface PackageImportPanelProps {
  defaultPackageId?: string;
}

export default function PackageImportPanel({ defaultPackageId = "demo" }: PackageImportPanelProps) {
  const { t } = useTranslation("platform");
  const queryClient = useQueryClient();
  const [packageId, setPackageId] = useState(defaultPackageId);
  const [manifestText, setManifestText] = useState(`{
  "version": "1.0.0",
  "displayName": "Demo package",
  "schemaName": "app_demo",
  "migrations": []
}`);

  const importMutation = useMutation({
    mutationFn: () => {
      const manifest = JSON.parse(manifestText) as unknown;
      return importPackage(packageId.trim(), manifest);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  return (
    <div className="package-import-panel">
      <h3>{t("packageImport.title")}</h3>
      <p className="op-muted">{t("packageImport.subtitle")}</p>
      <label>
        packageId
        <input value={packageId} onChange={(e) => setPackageId(e.target.value)} />
      </label>
      <label className="full">
        manifest JSON
        <textarea
          className="mono"
          rows={12}
          value={manifestText}
          onChange={(e) => setManifestText(e.target.value)}
          spellCheck={false}
        />
      </label>
      <div className="form-actions">
        <button
          type="button"
          className="btn primary"
          disabled={importMutation.isPending || !packageId.trim()}
          onClick={() => importMutation.mutate()}
        >
          {t("packageImport.import")}
        </button>
      </div>
      {importMutation.error && (
        <div className="op-alert op-alert-error">{(importMutation.error as Error).message}</div>
      )}
      {importMutation.data && (
        <pre className="mono small">{JSON.stringify(importMutation.data, null, 2)}</pre>
      )}
    </div>
  );
}
