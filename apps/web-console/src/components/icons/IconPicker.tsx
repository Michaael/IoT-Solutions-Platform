import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import ObjectTreeIcon, {
  TREE_ICON_CATALOG,
  isTreeIconId,
  type TreeIconKind,
} from "./ObjectTreeIcon";
import type { ObjectType } from "../../types";

interface IconPickerProps {
  path: string;
  type: ObjectType;
  value: string | null | undefined;
  onChange: (iconId: string | null) => void;
  disabled?: boolean;
}

export default function IconPicker({
  path,
  type,
  value,
  onChange,
  disabled = false,
}: IconPickerProps) {
  const { t } = useTranslation("explorer");
  const [open, setOpen] = useState(false);
  const categories = useMemo(() => {
    const map = new Map<string, typeof TREE_ICON_CATALOG>();
    for (const item of TREE_ICON_CATALOG) {
      const categoryLabel = t(`icons.category.${item.category}`);
      if (!map.has(categoryLabel)) {
        map.set(categoryLabel, []);
      }
      map.get(categoryLabel)!.push(item);
    }
    return [...map.entries()];
  }, [t]);

  const effective = isTreeIconId(value) ? value : null;
  const label = effective
    ? t(`icons.${effective}`)
    : t("icons.autoByType");

  const selectIcon = (iconId: string | null) => {
    onChange(iconId);
    setOpen(false);
  };

  return (
    <div className="icon-picker">
      <div className="icon-picker-current">
        <button
          type="button"
          className={`icon-picker-preview ${disabled ? "disabled" : ""}`}
          title={disabled ? label : t("icons.clickToSelect")}
          disabled={disabled}
          onClick={() => !disabled && setOpen((v) => !v)}
        >
          <ObjectTreeIcon path={path} type={type} iconId={effective} size={20} />
        </button>
        <span className="hint">{label}</span>
        {!disabled && open && effective && (
          <button type="button" className="btn small" onClick={() => selectIcon(null)}>
            {t("icons.reset")}
          </button>
        )}
      </div>
      {!disabled && open && (
        <div className="icon-picker-grid-wrap">
          <button
            type="button"
            className={`icon-picker-item ${effective === null ? "selected" : ""}`}
            title={t("icons.auto")}
            onClick={() => selectIcon(null)}
          >
            <ObjectTreeIcon path={path} type={type} size={18} />
            <span>{t("icons.auto")}</span>
          </button>
          {categories.map(([category, items]) => (
            <div key={category} className="icon-picker-group">
              <span className="icon-picker-group-title">{category}</span>
              <div className="icon-picker-grid">
                {items.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    className={`icon-picker-item ${effective === item.id ? "selected" : ""}`}
                    title={t(`icons.${item.id}`)}
                    onClick={() => selectIcon(item.id)}
                  >
                    <ObjectTreeIcon
                      path={path}
                      type={type}
                      iconId={item.id as TreeIconKind}
                      size={18}
                    />
                    <span>{t(`icons.${item.id}`)}</span>
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
