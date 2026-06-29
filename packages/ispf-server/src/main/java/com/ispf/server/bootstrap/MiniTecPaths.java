package com.ispf.server.bootstrap;

/**
 * Object paths for the generic mini-TEC reference solution.
 */
public final class MiniTecPaths {

    public static final String APP_ID = "mini-tec";
    public static final String PLANT_FOLDER_NAME = "mini-tec-plant";
    public static final String DISPLAY_NAME = "Мини-ТЭЦ (эталон)";
    public static final String FOLDER = "root.platform.devices." + PLANT_FOLDER_NAME;
    public static final String GPU_01 = FOLDER + ".gpu-01";
    public static final String GPU_02 = FOLDER + ".gpu-02";
    public static final String GPU_03 = FOLDER + ".gpu-03";
    public static final String GRPB = FOLDER + ".grpb";
    public static final String RUMB = FOLDER + ".rumb-10kv";
    public static final String DGU = FOLDER + ".dgu";
    public static final String LOAD_MODULE = FOLDER + ".load-module";
    public static final String STATION_HUB = FOLDER + ".station-hub";

    public static final String DASHBOARD_OVERVIEW = "root.platform.dashboards.mini-tec-overview";
    public static final String DASHBOARD_SINGLE_LINE = "root.platform.dashboards.mini-tec-single-line";
    public static final String MIMIC_SINGLE_LINE = "root.platform.mimics.mini-tec-single-line";
    public static final String DASHBOARD_GPU_DETAIL = "root.platform.dashboards.mini-tec-gpu-detail";
    public static final String DASHBOARD_GRPB = "root.platform.dashboards.mini-tec-grpb";
    public static final String DASHBOARD_RUMB = "root.platform.dashboards.mini-tec-rumb";
    public static final String DASHBOARD_DGU = "root.platform.dashboards.mini-tec-dgu";
    public static final String DASHBOARD_LOAD = "root.platform.dashboards.mini-tec-load-module";
    public static final String DASHBOARD_PROTECTIONS = "root.platform.dashboards.mini-tec-protections";
    public static final String DASHBOARD_EXPLOITATION = "root.platform.dashboards.mini-tec-exploitation";

    public static final String WORKFLOW_GAS_TRIP = "root.platform.workflows.mini-tec-gas-emergency-trip";
    public static final String WORKFLOW_LOAD_UNLOAD = "root.platform.workflows.mini-tec-load-module-auto-unload";
    public static final String WORKFLOW_GPU_START = "root.platform.workflows.mini-tec-gpu-start-sequence";
    public static final String WORKFLOW_ACK = "root.platform.workflows.mini-tec-ack-protection";

    private MiniTecPaths() {
    }
}
