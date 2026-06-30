import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  deployApplicationBundle,
  exportApplicationBundle,
  pullApplicationBundleFromTree,
  validateApplicationBundle,
} from "../api/applications";
import {
  appendBundleObject,
  listBundleSectionRows,
  parseManifestJson,
  removeBundleSectionItem,
  type BundleSectionKey,
} from "../utils/bundleManifestSections";
import { ObjectPathField } from "../ui";

interface ApplicationBundlePanelProps {
  appId: string;
  canManage: boolean;
}

export default function ApplicationBundlePanel({ appId, canManage }: ApplicationBundlePanelProps) {
  const { t } = useTranslation("platform");
  const queryClient = useQueryClient();
  const [manifestText, setManifestText] = useState("");
  const [validationMessage, setValidationMessage] = useState<string | null>(null);
  const [addParentPath, setAddParentPath] = useState("root.platform.devices");
  const [addName, setAddName] = useState("");
  const [addType, setAddType] = useState("DEVICE");
  const [pullPath, setPullPath] = useState("");
  const [pullMessage, setPullMessage] = useState<string | null>(null);

  const exportQuery = useQuery({
    queryKey: ["application-bundle-export", appId],
    queryFn: () => exportApplicationBundle(appId),
    enabled: Boolean(appId),
    retry: false,
  });

  useEffect(() => {
    if (exportQuery.data?.manifest) {
      setManifestText(JSON.stringify(exportQuery.data.manifest, null, 2));
    }
  }, [exportQuery.data]);

  const parsedManifest = useMemo(() => {
    try {
      return parseManifestJson(manifestText);
    } catch {
      return null;
    }
  }, [manifestText]);

  const sectionRows = useMemo(
    () => (parsedManifest ? listBundleSectionRows(parsedManifest) : []),
    [parsedManifest]
  );

  const validateMutation = useMutation({
    mutationFn: () => validateApplicationBundle(appId, parseManifestJson(manifestText), true),
    onSuccess: (result) => {
      const parts = [
        `${t("bundle.validateStatus")}: ${result.status}`,
        ...(result.errors ?? []).map((error) => `${t("bundle.validateError")}: ${error}`),
        ...(result.warnings ?? []).map((warning) => `${t("bundle.validateWarning")}: ${warning}`),
      ];
      setValidationMessage(parts.join("\n"));
    },
    onError: (error) => setValidationMessage(String(error)),
  });

  const deployMutation = useMutation({
    mutationFn: () => deployApplicationBundle(appId, parseManifestJson(manifestText)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["application-bundle-export", appId] });
      queryClient.invalidateQueries({ queryKey: ["deploy-history", appId] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  const pullMutation = useMutation({
    mutationFn: (options?: { sections?: string[]; paths?: string[] }) =>
      pullApplicationBundleFromTree(appId, { ...options, mergeActive: true }),
    onSuccess: (result) => {
      setManifestText(JSON.stringify(result.manifest, null, 2));
      const summary = Object.entries(result.pulled ?? {})
        .map(([section, count]) => `${section}: ${count}`)
        .join(", ");
      const warnings = (result.warnings ?? []).join("\n");
      setPullMessage(
        [t("bundle.pullSuccess", { summary }), warnings].filter(Boolean).join("\n")
      );
    },
    onError: (error) => setPullMessage(String(error)),
  });

  const downloadManifest = () => {
    const blob = new Blob([manifestText], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${appId}-bundle.json`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  const reloadFromServer = () => {
    void exportQuery.refetch();
  };

  const removeRow = (section: BundleSectionKey, index: number) => {
    if (!parsedManifest) {
      return;
    }
    const next = removeBundleSectionItem(parsedManifest, section, index);
    setManifestText(JSON.stringify(next, null, 2));
  };

  const addObject = () => {
    if (!parsedManifest || !addName.trim()) {
      return;
    }
    const next = appendBundleObject(parsedManifest, {
      parentPath: addParentPath.trim(),
      name: addName.trim(),
      type: addType.trim(),
    });
    setManifestText(JSON.stringify(next, null, 2));
    setAddName("");
  };

  return (
    <div className="application-bundle-panel">
      <h3>{t("bundle.title")}</h3>
      <p className="op-muted">{t("bundle.subtitle", { appId })}</p>

      {exportQuery.isLoading && <p className="op-muted">{t("bundle.loading")}</p>}
      {exportQuery.error && (
        <div className="op-alert op-alert-error">{String(exportQuery.error)}</div>
      )}
      {exportQuery.data && (
        <p className="op-muted">
          {t("bundle.activeVersion", {
            version: exportQuery.data.version,
            deployedAt: exportQuery.data.deployedAt,
          })}
        </p>
      )}

      <div className="form-actions">
        <button type="button" className="btn" onClick={reloadFromServer} disabled={exportQuery.isFetching}>
          {t("bundle.reload")}
        </button>
        <button type="button" className="btn" onClick={downloadManifest} disabled={!manifestText.trim()}>
          {t("bundle.download")}
        </button>
        {canManage && (
          <>
            <button
              type="button"
              className="btn"
              disabled={pullMutation.isPending}
              onClick={() => pullMutation.mutate(undefined)}
            >
              {pullMutation.isPending ? t("bundle.pulling") : t("bundle.pullFromTree")}
            </button>
            <button
              type="button"
              className="btn"
              disabled={validateMutation.isPending || !manifestText.trim()}
              onClick={() => validateMutation.mutate()}
            >
              {validateMutation.isPending ? t("bundle.validating") : t("bundle.validate")}
            </button>
            <button
              type="button"
              className="btn primary"
              disabled={deployMutation.isPending || !manifestText.trim()}
              onClick={() => {
                if (!window.confirm(t("bundle.deployConfirm"))) {
                  return;
                }
                deployMutation.mutate();
              }}
            >
              {deployMutation.isPending ? t("bundle.deploying") : t("bundle.deploy")}
            </button>
          </>
        )}
      </div>

      <label className="full">
        manifest JSON
        <textarea
          className="mono"
          rows={14}
          value={manifestText}
          onChange={(event) => setManifestText(event.target.value)}
          spellCheck={false}
          readOnly={!canManage}
        />
      </label>

      {validationMessage && <pre className="mono small validation-output">{validationMessage}</pre>}
      {pullMessage && <pre className="mono small validation-output">{pullMessage}</pre>}
      {deployMutation.error && (
        <div className="op-alert op-alert-error">{String(deployMutation.error)}</div>
      )}
      {deployMutation.data && (
        <pre className="mono small">{JSON.stringify(deployMutation.data, null, 2)}</pre>
      )}

      {canManage && parsedManifest && (
        <>
          <hr />
          <h4>{t("bundle.sectionsTitle")}</h4>
          <p className="op-muted">{t("bundle.sectionsHint")}</p>
          {sectionRows.length === 0 ? (
            <p className="op-muted">{t("bundle.sectionsEmpty")}</p>
          ) : (
            <table className="data-table compact">
              <thead>
                <tr>
                  <th>{t("bundle.sectionColumn")}</th>
                  <th>{t("bundle.itemColumn")}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {sectionRows.map((row) => (
                  <tr key={`${row.section}-${row.index}-${row.label}`}>
                    <td><code>{row.section}</code></td>
                    <td><code>{row.label}</code></td>
                    <td>
                      <button
                        type="button"
                        className="btn"
                        onClick={() => {
                          if (window.confirm(t("bundle.removeConfirm", { item: row.label }))) {
                            removeRow(row.section, row.index);
                          }
                        }}
                      >
                        {t("bundle.remove")}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          <h4>{t("bundle.addObjectTitle")}</h4>
          <div className="bundle-add-object-form">
            <label>
              parentPath
              <input value={addParentPath} onChange={(event) => setAddParentPath(event.target.value)} />
            </label>
            <label>
              name
              <input value={addName} onChange={(event) => setAddName(event.target.value)} />
            </label>
            <label>
              type
              <input value={addType} onChange={(event) => setAddType(event.target.value)} />
            </label>
            <button type="button" className="btn" disabled={!addName.trim()} onClick={addObject}>
              {t("bundle.addObject")}
            </button>
          </div>

          <h4>{t("bundle.pullPathTitle")}</h4>
          <p className="op-muted">{t("bundle.pullPathHint")}</p>
          <div className="bundle-add-object-form">
            <ObjectPathField
              className="full"
              label="objectPath"
              value={pullPath}
              onChange={setPullPath}
              placeholder="root.platform.dashboards.my-dashboard"
            />
            <button
              type="button"
              className="btn"
              disabled={pullMutation.isPending || !pullPath.trim()}
              onClick={() => {
                const path = pullPath.trim();
                const section = inferSectionFromPath(path);
                pullMutation.mutate({ sections: [section], paths: [path] });
              }}
            >
              {t("bundle.pullPath")}
            </button>
          </div>
        </>
      )}
    </div>
  );
}

function inferSectionFromPath(path: string): string {
  if (path.includes(".dashboards.")) {
    return "dashboards";
  }
  if (path.includes(".workflows.")) {
    return "workflows";
  }
  if (path.includes(".reports.")) {
    return "reports";
  }
  if (path.includes(".alert-rules.")) {
    return "alertRules";
  }
  if (path.includes(".correlators.")) {
    return "correlators";
  }
  if (path.includes(".schedules.")) {
    return "schedules";
  }
  if (path.includes(".bindings.")) {
    return "bindings";
  }
  if (path.includes(".migrations.")) {
    return "migrations";
  }
  return "objects";
}
