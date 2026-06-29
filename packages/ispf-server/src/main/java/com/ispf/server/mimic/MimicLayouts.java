package com.ispf.server.mimic;

/**
 * Default SCADA mimic diagram JSON (empty canvas).
 */
public final class MimicLayouts {

    public static final String EMPTY_MIMIC = """
            {
              "version": 1,
              "width": 1600,
              "height": 900,
              "background": "var(--bg)",
              "grid": { "size": 20, "snap": true, "visible": true },
              "layers": [{ "id": "layer-default", "name": "Main", "visible": true }],
              "elements": [],
              "connections": []
            }
            """.trim();

    private MimicLayouts() {
    }
}
