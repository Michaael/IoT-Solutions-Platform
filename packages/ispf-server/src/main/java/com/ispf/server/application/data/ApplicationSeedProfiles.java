package com.ispf.server.application.data;

import java.util.List;

public final class ApplicationSeedProfiles {

    private ApplicationSeedProfiles() {
    }

    public static List<ApplicationDataService.SeedScript> scripts(String profile) {
        return switch (profile) {
            case "smoke-p301" -> smokeP301();
            default -> throw new IllegalArgumentException("Unknown seed profile: " + profile);
        };
    }

    private static List<ApplicationDataService.SeedScript> smokeP301() {
        return List.of(
                new ApplicationDataService.SeedScript(
                        "shift",
                        """
                                INSERT INTO dispatch_shift (id, shift_code, status, started_at)
                                SELECT '11111111-1111-1111-1111-111111111111', 'SHIFT-2026-A', 'open', CURRENT_TIMESTAMP
                                WHERE NOT EXISTS (
                                  SELECT 1 FROM dispatch_shift WHERE shift_code = 'SHIFT-2026-A'
                                );
                                """
                ),
                new ApplicationDataService.SeedScript(
                        "orders",
                        """
                                INSERT INTO dispatch_order (id, order_number, status, shift_id, tank_path)
                                SELECT '22222222-2222-2222-2222-222222222201', 'DO-2026-001', 'ready',
                                       '11111111-1111-1111-1111-111111111111', 'root.platform.terminal.tanks.t1'
                                WHERE NOT EXISTS (SELECT 1 FROM dispatch_order WHERE order_number = 'DO-2026-001');
                                INSERT INTO dispatch_order (id, order_number, status, shift_id, tank_path)
                                SELECT '22222222-2222-2222-2222-222222222202', 'DO-2026-002', 'assigned',
                                       '11111111-1111-1111-1111-111111111111', 'root.platform.terminal.tanks.t2'
                                WHERE NOT EXISTS (SELECT 1 FROM dispatch_order WHERE order_number = 'DO-2026-002');
                                """
                ),
                new ApplicationDataService.SeedScript(
                        "tanks",
                        """
                                INSERT INTO tank_balance (tank_path, product_code, volume_liters, quality_status)
                                SELECT 'root.platform.terminal.tanks.t1', 'DIESEL', 120000, 'approved'
                                WHERE NOT EXISTS (SELECT 1 FROM tank_balance WHERE tank_path = 'root.platform.terminal.tanks.t1');
                                INSERT INTO tank_balance (tank_path, product_code, volume_liters, quality_status)
                                SELECT 'root.platform.terminal.tanks.t2', 'GASOLINE', 95000, 'approved'
                                WHERE NOT EXISTS (SELECT 1 FROM tank_balance WHERE tank_path = 'root.platform.terminal.tanks.t2');
                                """
                )
        );
    }
}
