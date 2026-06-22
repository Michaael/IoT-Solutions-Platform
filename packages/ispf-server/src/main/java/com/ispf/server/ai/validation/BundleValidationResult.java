package com.ispf.server.ai.validation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record BundleValidationResult(
        String status,
        List<String> errors,
        List<String> warnings,
        List<String> wouldApply
) {
    public static final String OK = "OK";
    public static final String ERROR = "ERROR";

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status);
        map.put("errors", errors);
        map.put("warnings", warnings);
        if (wouldApply != null && !wouldApply.isEmpty()) {
            map.put("wouldApply", wouldApply);
        }
        return map;
    }

    public static BundleValidationResult ok(List<String> warnings, List<String> wouldApply) {
        return new BundleValidationResult(OK, List.of(), List.copyOf(warnings), List.copyOf(wouldApply));
    }

    public static BundleValidationResult error(List<String> errors, List<String> warnings, List<String> wouldApply) {
        return new BundleValidationResult(
                ERROR,
                List.copyOf(errors),
                List.copyOf(warnings),
                List.copyOf(wouldApply)
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> wouldApply = new ArrayList<>();

        public Builder addError(String message) {
            errors.add(message);
            return this;
        }

        public Builder addWarning(String message) {
            warnings.add(message);
            return this;
        }

        public Builder addWouldApply(String section) {
            wouldApply.add(section);
            return this;
        }

        public BundleValidationResult build() {
            if (errors.isEmpty()) {
                return ok(warnings, wouldApply);
            }
            return BundleValidationResult.error(errors, warnings, wouldApply);
        }
    }
}
