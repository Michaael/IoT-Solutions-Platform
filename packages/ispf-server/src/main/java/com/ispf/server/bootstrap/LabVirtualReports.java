package com.ispf.server.bootstrap;

import com.ispf.server.report.ReportService;

import java.util.List;

/**
 * Tree-variables test reports for {@link LabPlatformBootstrap} virtual lab devices.
 */
public final class LabVirtualReports {

    public static final String LAB_DEVICE_PATTERN = "root.platform.devices.lab-*";

    public static final String ALL_DEVICES_TABLE = "lab-all-devices-table";
    public static final String TABLE_CORRECTIVE = "lab-table-corrective";
    public static final String SINE_SNAPSHOT = "lab-virtual-sine";
    public static final String WAVES_SUM = "lab-virtual-waves-sum";
    public static final String DEVICE_STATUS = "lab-virtual-status";

    private static final List<ReportService.ReportColumn> TABLE_COLUMNS = List.of(
            new ReportService.ReportColumn("devicepath", "Device path"),
            new ReportService.ReportColumn("int", "Int"),
            new ReportService.ReportColumn("string", "String")
    );

    private static final List<ReportService.ReportColumn> VALUE_COLUMNS = List.of(
            new ReportService.ReportColumn("devicepath", "Device path"),
            new ReportService.ReportColumn("value", "Value")
    );

    private static final List<ReportService.ReportColumn> STATUS_COLUMNS = List.of(
            new ReportService.ReportColumn("devicepath", "Device path"),
            new ReportService.ReportColumn("online", "Online"),
            new ReportService.ReportColumn("lastseen", "Last seen")
    );

    private LabVirtualReports() {
    }

    public static void deployAll(ReportService reportService) {
        reportService.deployTreeVariables(
                ALL_DEVICES_TABLE,
                "All lab devices table",
                "Tree-variables report: table rows from all lab virtual devices",
                LAB_DEVICE_PATTERN,
                "table",
                TABLE_COLUMNS,
                1000
        );
        reportService.deployTreeVariables(
                TABLE_CORRECTIVE,
                "Corrective table report",
                "Opened when table int sum exceeds 100 (lab training)",
                LAB_DEVICE_PATTERN,
                "table",
                TABLE_COLUMNS,
                500
        );
        reportService.deployTreeVariables(
                SINE_SNAPSHOT,
                "Lab sine wave snapshot",
                "Current sineWave value from each lab virtual device",
                LAB_DEVICE_PATTERN,
                "sineWave",
                VALUE_COLUMNS,
                100
        );
        reportService.deployTreeVariables(
                WAVES_SUM,
                "Lab waves sum",
                "sumWaves (sine + sawtooth) from each lab virtual device",
                LAB_DEVICE_PATTERN,
                "sumWaves",
                VALUE_COLUMNS,
                100
        );
        reportService.deployTreeVariables(
                DEVICE_STATUS,
                "Lab device status",
                "Connectivity status from each lab virtual device",
                LAB_DEVICE_PATTERN,
                "status",
                STATUS_COLUMNS,
                100
        );
    }
}
