import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import { COMMON_HAYSTACK_MARKERS } from "../../constants/haystackMarkers";
import { searchHaystackTags, type HaystackTagSearchMatch } from "../../api/haystackSearch";
import type { DashboardLayout, DashboardWidget } from "../../types/dashboard";
import { newWidget } from "../../types/dashboard";
import { nextWidgetZIndex } from "./widgetLayerUtils";

interface HaystackBindDialogProps {
  layout: DashboardLayout;
  onApply: (layout: DashboardLayout) => void;
  onClose: () => void;
}

const DEFAULT_ROOT_PATH = "root.platform";
const BIND_TAGS = ["equip", "point", "temp"] as const;

function appendValueWidgets(
  layout: DashboardLayout,
  matches: HaystackTagSearchMatch[]
): DashboardLayout {
  const pointMatches = matches.filter(
    (match) => match.entityKind === "point" && match.variableName
  );
  if (pointMatches.length === 0) {
    return layout;
  }
  const columns = layout.columns ?? 12;
  const widgetW = 4;
  const widgetH = 2;
  const baseY = layout.widgets.reduce((max, widget) => Math.max(max, widget.y + widget.h), 0);
  const baseZ = nextWidgetZIndex(layout.widgets);
  const newWidgets: DashboardWidget[] = pointMatches.map((match, index) => {
    const slot = layout.widgets.length + index;
    const x = (slot * widgetW) % columns;
    const row = Math.floor((slot * widgetW) / columns);
    return {
      ...newWidget("value", slot),
      id: `haystack-${match.variableName}-${index}-${Date.now()}`,
      title: match.dis || match.variableName || match.objectPath,
      objectPath: match.objectPath,
      variableName: match.variableName,
      valueField: "value",
      decimals: 2,
      x,
      y: baseY + row * widgetH,
      w: widgetW,
      h: widgetH,
      zIndex: baseZ + index,
      visible: true,
    };
  });
  return { ...layout, widgets: [...layout.widgets, ...newWidgets] };
}

export default function HaystackBindDialog({ layout, onApply, onClose }: HaystackBindDialogProps) {
  const { t } = useTranslation(["dashboard", "inspector"]);
  const [selectedTags, setSelectedTags] = useState<string[]>([...BIND_TAGS]);
  const [rootPath, setRootPath] = useState(DEFAULT_ROOT_PATH);
  const [matches, setMatches] = useState<HaystackTagSearchMatch[]>([]);

  const searchMutation = useMutation({
    mutationFn: () =>
      searchHaystackTags({
        tags: selectedTags,
        rootPath,
        entityKind: "point",
        limit: 50,
      }),
    onSuccess: (result) => {
      setMatches(result.matches);
    },
  });

  const pointCount = useMemo(
    () => matches.filter((match) => match.entityKind === "point" && match.variableName).length,
    [matches]
  );

  const toggleTag = (tag: string) => {
    setSelectedTags((current) =>
      current.includes(tag) ? current.filter((item) => item !== tag) : [...current, tag]
    );
  };

  const handleSearch = () => {
    if (selectedTags.length === 0) {
      return;
    }
    searchMutation.mutate();
  };

  const handleBind = () => {
    onApply(appendValueWidgets(layout, matches));
    onClose();
  };

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="modal-dialog haystack-bind-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="haystack-bind-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="modal-header">
          <h2 id="haystack-bind-title">{t("haystackBind.title")}</h2>
          <button type="button" className="btn" onClick={onClose}>
            {t("modal.close")}
          </button>
        </header>

        <p className="hint">{t("haystackBind.intro")}</p>

        <fieldset className="function-form-multiselect haystack-marker-fieldset">
          <legend>{t("inspector:haystack.tags")}</legend>
          {COMMON_HAYSTACK_MARKERS.map((tag) => (
            <label key={tag} className="checkbox-inline">
              <input
                type="checkbox"
                checked={selectedTags.includes(tag)}
                onChange={() => toggleTag(tag)}
              />
              {t(`inspector:haystack.marker.${tag}`, tag)}
            </label>
          ))}
        </fieldset>

        <label className="property-field">
          {t("haystackBind.rootPath")}
          <input
            type="text"
            value={rootPath}
            onChange={(event) => setRootPath(event.target.value)}
            placeholder={DEFAULT_ROOT_PATH}
          />
        </label>

        <div className="haystack-bind-actions">
          <button
            type="button"
            className="btn primary"
            disabled={selectedTags.length === 0 || searchMutation.isPending}
            onClick={handleSearch}
          >
            {searchMutation.isPending ? t("haystackBind.searching") : t("haystackBind.search")}
          </button>
        </div>

        {searchMutation.isError && (
          <p className="error">{t("haystackBind.error")}</p>
        )}

        {matches.length > 0 && (
          <p className="hint">
            {t("haystackBind.results", { count: pointCount })}
          </p>
        )}

        {pointCount > 0 && (
          <ul className="haystack-bind-preview">
            {matches
              .filter((match) => match.entityKind === "point" && match.variableName)
              .slice(0, 8)
              .map((match) => (
                <li key={`${match.objectPath}.${match.variableName}`}>
                  {match.dis || match.variableName} — {match.objectPath}
                </li>
              ))}
          </ul>
        )}

        <footer className="modal-footer">
          <button type="button" className="btn" onClick={onClose}>
            {t("common:action.cancel")}
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={pointCount === 0}
            onClick={handleBind}
          >
            {t("haystackBind.addWidgets", { count: pointCount })}
          </button>
        </footer>
      </div>
    </div>
  );
}
