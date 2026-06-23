package com.ispf.server.api.dto;

import com.ispf.core.object.PlatformObject;
import com.ispf.server.federation.FederationProxyMetadata;
import com.ispf.core.object.ObjectType;
import com.ispf.server.object.BindingStateVariables;
import com.ispf.server.object.ObjectUiIconService;
import com.ispf.server.plugin.model.dto.AppliedModelDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ObjectDto(
        String id,
        String path,
        ObjectType type,
        String displayName,
        String description,
        String templateId,
        String iconId,
        Instant createdAt,
        int sortOrder,
        long revision,
        String lastChangedBy,
        Instant lastChangedAt,
        List<String> variableNames,
        List<String> eventNames,
        boolean federated,
        String federationPeerId,
        String federationRemotePath,
        List<AppliedModelDto> appliedModels
) {
    public static ObjectDto from(PlatformObject node) {
        return from(node, null, List.of());
    }

    public static ObjectDto from(PlatformObject node, String iconId) {
        return from(node, iconId, List.of());
    }

    public static ObjectDto from(PlatformObject node, String iconId, List<AppliedModelDto> appliedModels) {
        return from(node, iconId, appliedModels, false);
    }

    /** Lightweight list payload without variable/event name lists. */
    public static ObjectDto fromLite(PlatformObject node, String iconId) {
        return from(node, iconId, List.of(), true);
    }

    public static ObjectDto fromLite(PlatformObject node, String iconId, List<AppliedModelDto> appliedModels) {
        return from(node, iconId, appliedModels, true);
    }

    private static ObjectDto from(PlatformObject node, String iconId, List<AppliedModelDto> appliedModels, boolean lite) {
        boolean federated = FederationProxyMetadata.isProxy(node);
        return new ObjectDto(
                node.id(),
                node.path(),
                node.type(),
                node.displayName(),
                node.description(),
                node.templateId().orElse(null),
                iconId,
                node.createdAt(),
                node.sortOrder(),
                node.revision(),
                node.lastChangedBy(),
                node.lastChangedAt(),
                lite ? List.of() : node.variables().keySet().stream()
                        .filter(name -> !ObjectUiIconService.UI_ICON_VARIABLE.equals(name))
                        .filter(name -> !BindingStateVariables.isReserved(name))
                        .filter(name -> !federated || !FederationProxyMetadata.isFederationVariable(name))
                        .sorted()
                        .toList(),
                lite ? List.of() : node.events().keySet().stream().sorted().toList(),
                federated,
                federated ? FederationProxyMetadata.peerId(node).map(UUID::toString).orElse(null) : null,
                federated ? FederationProxyMetadata.remotePath(node).orElse(null) : null,
                appliedModels != null ? appliedModels : List.of()
        );
    }
}
