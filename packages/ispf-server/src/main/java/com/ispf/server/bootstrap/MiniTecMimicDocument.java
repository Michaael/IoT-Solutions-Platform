package com.ispf.server.bootstrap;

/**
 * SCADA mimic document JSON for mini-TEC single-line diagram (scada-mimic widget).
 */
public final class MiniTecMimicDocument {

    public static final String DIAGRAM_JSON = """
            {"version":1,"width":1200,"height":400,"background":"var(--bg)","grid":{"size":20,"snap":false,"visible":false},"layers":[{"id":"layer-default","name":"Main","visible":true}],"elements":[{"id":"busbar-main","symbolId":"busbar.horizontal","layerId":"layer-default","x":248,"y":114,"bindings":{"energized":{"objectPath":"%1$s","variableName":"islandMode","valueField":"value","transform":"bool"}},"props":{}},{"id":"gen-gpu1","symbolId":"gen.block","layerId":"layer-default","x":268,"y":24,"props":{"label":"GPU-1","ratedKw":1480},"bindings":{"running":{"objectPath":"%2$s","variableName":"running","valueField":"value","transform":"bool"},"power":{"objectPath":"%2$s","variableName":"activePowerKw","valueField":"value","transform":"number"}}},{"id":"gen-gpu2","symbolId":"gen.block","layerId":"layer-default","x":418,"y":24,"props":{"label":"GPU-2","ratedKw":1480},"bindings":{"running":{"objectPath":"%3$s","variableName":"running","valueField":"value","transform":"bool"},"power":{"objectPath":"%3$s","variableName":"activePowerKw","valueField":"value","transform":"number"}}},{"id":"gen-gpu3","symbolId":"gen.block","layerId":"layer-default","x":568,"y":24,"props":{"label":"GPU-3","ratedKw":1480},"bindings":{"running":{"objectPath":"%4$s","variableName":"running","valueField":"value","transform":"bool"},"power":{"objectPath":"%4$s","variableName":"activePowerKw","valueField":"value","transform":"number"}}},{"id":"dgu-block","symbolId":"gen.block","layerId":"layer-default","x":488,"y":176,"props":{"label":"DGU","ratedKw":500},"bindings":{"running":{"objectPath":"%5$s","variableName":"running","valueField":"value","transform":"bool"},"power":{"objectPath":"%5$s","variableName":"activePowerKw","valueField":"value","transform":"number"}}},{"id":"breaker-rumb","symbolId":"breaker","layerId":"layer-default","x":872,"y":168,"bindings":{"closed":{"objectPath":"%6$s","variableName":"breakerClosed","valueField":"value","transform":"bool"}},"actions":[{"id":"toggle-breaker","type":"invokeFunction","objectPath":"%6$s","functionName":"breaker_operate"}]},{"id":"load-block","symbolId":"load.block","layerId":"layer-default","x":920,"y":240,"props":{"label":"Load"},"bindings":{"power":{"objectPath":"%7$s","variableName":"activePowerKw","valueField":"value","transform":"number"}}},{"id":"hub-freq","symbolId":"value-badge","layerId":"layer-default","x":540,"y":88,"props":{"unit":" Hz","decimals":2},"bindings":{"value":{"objectPath":"%1$s","variableName":"gridFrequencyHz","valueField":"value","transform":"number"}}},{"id":"hub-power","symbolId":"value-badge","layerId":"layer-default","x":640,"y":88,"props":{"unit":" kW","decimals":0},"bindings":{"value":{"objectPath":"%1$s","variableName":"totalGenPowerKw","valueField":"value","transform":"number"}}}],"connections":[]}
            """.formatted(
            MiniTecPaths.STATION_HUB,
            MiniTecPaths.GPU_01,
            MiniTecPaths.GPU_02,
            MiniTecPaths.GPU_03,
            MiniTecPaths.DGU,
            MiniTecPaths.RUMB,
            MiniTecPaths.LOAD_MODULE
    ).trim();

    private MiniTecMimicDocument() {
    }
}
