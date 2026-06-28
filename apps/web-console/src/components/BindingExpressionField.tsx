import { useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import { validateExpression } from "../api";
import {
  PLATFORM_BINDING_ENTRIES,
  filterPlatformBindings,
  suggestPlatformBindingPrefix,
} from "../utils/platformBindings";

interface BindingExpressionFieldProps {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
}

function insertAtSelection(
  current: string,
  snippet: string,
  input: HTMLInputElement | null
): string {
  if (!input) {
    return snippet;
  }
  const start = input.selectionStart ?? current.length;
  const end = input.selectionEnd ?? start;
  const next = `${current.slice(0, start)}${snippet}${current.slice(end)}`;
  window.requestAnimationFrame(() => {
    const caret = start + snippet.length;
    input.setSelectionRange(caret, caret);
    input.focus();
  });
  return next;
}

export default function BindingExpressionField({
  id,
  value,
  onChange,
  placeholder,
  disabled = false,
}: BindingExpressionFieldProps) {
  const { t } = useTranslation("inspector");
  const inputRef = useRef<HTMLInputElement>(null);
  const [catalogOpen, setCatalogOpen] = useState(false);
  const [catalogQuery, setCatalogQuery] = useState("");

  const validateMutation = useMutation({
    mutationFn: () => validateExpression(value.trim()),
  });

  const catalogEntries = useMemo(
    () => filterPlatformBindings(catalogQuery),
    [catalogQuery]
  );

  const inlineSuggestions = useMemo(
    () => suggestPlatformBindingPrefix(value),
    [value]
  );

  const showInlineSuggestions =
    !disabled && value.trim().length > 0 && inlineSuggestions.length > 0 && inlineSuggestions.length < 18;

  const applySnippet = (snippet: string) => {
    onChange(insertAtSelection(value, snippet, inputRef.current));
    setCatalogOpen(false);
    setCatalogQuery("");
  };

  return (
    <div className="binding-expression-field">
      <div className="binding-expression-input-row">
        <input
          ref={inputRef}
          id={id}
          type="text"
          className="mono"
          value={value}
          disabled={disabled}
          placeholder={placeholder ?? t("bindingExpression.placeholder")}
          list={id ? `${id}-platform-bindings` : "platform-bindings-list"}
          onChange={(e) => onChange(e.target.value)}
        />
        <datalist id={id ? `${id}-platform-bindings` : "platform-bindings-list"}>
          {PLATFORM_BINDING_ENTRIES.map((entry) => (
            <option key={entry.id} value={`${entry.name}(`}>
              {entry.snippet}
            </option>
          ))}
        </datalist>
        <button
          type="button"
          className="btn small"
          disabled={disabled}
          onClick={() => setCatalogOpen((open) => !open)}
        >
          {catalogOpen ? t("platformBindings.hideCatalog") : t("platformBindings.showCatalog")}
        </button>
        <button
          type="button"
          className="btn small"
          disabled={disabled || !value.trim() || validateMutation.isPending}
          onClick={() => validateMutation.mutate()}
        >
          {t("bindingExpression.validate")}
        </button>
      </div>

      {showInlineSuggestions && (
        <div className="binding-expression-suggestions">
          {inlineSuggestions.slice(0, 6).map((entry) => (
            <button
              key={entry.id}
              type="button"
              className="btn btn-sm binding-expression-suggestion"
              disabled={disabled}
              onClick={() => applySnippet(entry.snippet)}
            >
              <code>{entry.name}</code>
            </button>
          ))}
        </div>
      )}

      {catalogOpen && (
        <div className="platform-binding-catalog">
          <div className="platform-binding-catalog-toolbar">
            <input
              type="search"
              value={catalogQuery}
              placeholder={t("platformBindings.search")}
              onChange={(e) => setCatalogQuery(e.target.value)}
            />
            <span className="hint">{t("platformBindings.count", { count: catalogEntries.length })}</span>
          </div>
          <ul className="platform-binding-catalog-list">
            {catalogEntries.map((entry) => (
              <li key={entry.id}>
                <button
                  type="button"
                  className="platform-binding-catalog-item"
                  disabled={disabled}
                  onClick={() => applySnippet(entry.snippet)}
                >
                  <div className="platform-binding-catalog-head">
                    <code>{entry.name}</code>
                    <span className="platform-binding-catalog-category">
                      {t(`platformBindings.category.${entry.category}`)}
                    </span>
                    {entry.stateful ? (
                      <span className="inline-badge">{t("platformBindings.stateful")}</span>
                    ) : null}
                  </div>
                  <code className="platform-binding-catalog-snippet">{entry.snippet}</code>
                  <p className="hint">{t(`platformBindings.${entry.id}.desc`)}</p>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      {validateMutation.data?.valid && (
        <span className="hint success">{t("bindingExpression.valid")}</span>
      )}
      {validateMutation.data && !validateMutation.data.valid && (
        <span className="hint error">{validateMutation.data.error ?? t("bindingExpression.error")}</span>
      )}
      {validateMutation.error && (
        <span className="hint error">{(validateMutation.error as Error).message}</span>
      )}
    </div>
  );
}
