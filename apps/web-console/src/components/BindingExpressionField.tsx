import { useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { validateExpression } from "../api";

interface BindingExpressionFieldProps {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
}

export default function BindingExpressionField({
  id,
  value,
  onChange,
  placeholder,
  disabled = false,
}: BindingExpressionFieldProps) {
  const { t } = useTranslation("inspector");
  const validateMutation = useMutation({
    mutationFn: () => validateExpression(value.trim()),
  });

  return (
    <div className="binding-expression-field">
      <input
        id={id}
        type="text"
        className="mono"
        value={value}
        disabled={disabled}
        placeholder={placeholder ?? t("bindingExpression.placeholder")}
        onChange={(e) => onChange(e.target.value)}
      />
      <button
        type="button"
        className="btn small"
        disabled={disabled || !value.trim() || validateMutation.isPending}
        onClick={() => validateMutation.mutate()}
      >
        {t("bindingExpression.validate")}
      </button>
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
