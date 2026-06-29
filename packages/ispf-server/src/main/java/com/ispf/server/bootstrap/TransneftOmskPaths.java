package com.ispf.server.bootstrap;

/**
 * Object paths for the Transneft Omsk RDP SCADA mimic demo (scada-mimic widget).
 */
public final class TransneftOmskPaths {

    public static final String APP_ID = "transneft-omsk";
    public static final String PLANT_FOLDER_NAME = "transneft-omsk-rdp";
    public static final String DISPLAY_NAME = "Транснефть — РДП Омск (демо)";
    public static final String FOLDER = "root.platform.devices." + PLANT_FOLDER_NAME;
    public static final String RDP_HUB = FOLDER + ".rdp-hub";

    public static final String MIMIC_RDP = "root.platform.mimics.transneft-omsk-rdp";
    public static final String DASHBOARD_RDP = "root.platform.dashboards.transneft-omsk-rdp";

    public static String tank(int number) {
        return FOLDER + ".tank-" + number;
    }

    private TransneftOmskPaths() {
    }
}
