import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { ObjectType } from "../types";
import ObjectTreePickerDialog from "./ObjectTreePickerDialog";

export interface ObjectPathOption {
  path: string;
  displayName: string;
}

export interface ObjectPathFieldProps {
  id?: string;
  label?: string;
  value: string;
  onChange: (path: string) => void;
  objects?: ObjectPathOption[];
  filterTypes?: ObjectType[];
  rootPath?: string;
  placeholder?: string;
  disabled?: boolean;
  allowManual?: boolean;
  className?: string;
}

export default function ObjectPathField({
  id,
  label,
  value,
  onChange,
  objects,
  filterTypes,
  rootPath,
  placeholder,
  disabled = false,
  allowManual = true,
  className = "",
}: ObjectPathFieldProps) {
  const { t } = useTranslation("common");
  const [pickerOpen, setPickerOpen] = useState(false);

  return (
    <>
      <label className={`object-path-field ${className}`.trim()} htmlFor={id}>
        {label && <span className="field-caption">{label}</span>}
        <div className="object-path-field-controls">
          {objects && objects.length > 0 && (
            <select
              value={value}
              disabled={disabled}
              onChange={(event) => onChange(event.target.value)}
            >
              <option value="">—</option>
              {objects.map((obj) => (
                <option key={obj.path} value={obj.path}>
                  {obj.displayName}
                </option>
              ))}
            </select>
          )}
          {allowManual && (
            <input
              id={id}
              type="text"
              value={value}
              disabled={disabled}
              placeholder={placeholder ?? t("objectPath.placeholder")}
              onChange={(event) => onChange(event.target.value)}
            />
          )}
          <button
            type="button"
            className="btn small object-path-browse"
            disabled={disabled}
            title={t("objectPath.browseTree")}
            aria-label={t("objectPath.browseTree")}
            onClick={() => setPickerOpen(true)}
          >
            …
          </button>
        </div>
      </label>
      <ObjectTreePickerDialog
        open={pickerOpen}
        onClose={() => setPickerOpen(false)}
        onSelect={onChange}
        filterTypes={filterTypes}
        rootPath={rootPath}
      />
    </>
  );
}
