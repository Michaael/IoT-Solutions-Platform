package com.ispf.plugin.model;

/**
 * Tree paths for the three model catalog kinds (blueprint views).
 */
public final class ModelCatalogRoots {

    public static final String RELATIVE = "root.platform.relative-models";
    public static final String INSTANCE = "root.platform.instance-types";
    public static final String ABSOLUTE = "root.platform.absolute-models";
    /** @deprecated migrated to typed catalogs; kept for backward-compatible path checks */
    public static final String LEGACY = "root.platform.models";
    public static final String INSTANCES = "root.platform.instances";

    private ModelCatalogRoots() {
    }

    public static String catalogRoot(ModelType type) {
        return switch (type) {
            case RELATIVE -> RELATIVE;
            case INSTANCE -> INSTANCE;
            case ABSOLUTE -> ABSOLUTE;
        };
    }

    public static String catalogTitle(ModelType type) {
        return switch (type) {
            case RELATIVE -> "Relative Models";
            case INSTANCE -> "Instance Types";
            case ABSOLUTE -> "Absolute Models";
        };
    }

    public static boolean isCatalogPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return path.equals(RELATIVE) || path.startsWith(RELATIVE + ".")
                || path.equals(INSTANCE) || path.startsWith(INSTANCE + ".")
                || path.equals(ABSOLUTE) || path.startsWith(ABSOLUTE + ".")
                || path.equals(LEGACY) || path.startsWith(LEGACY + ".");
    }

    public static boolean isDefinitionPath(String path) {
        return isCatalogPath(path) && !path.equals(RELATIVE) && !path.equals(INSTANCE)
                && !path.equals(ABSOLUTE) && !path.equals(LEGACY);
    }
}
