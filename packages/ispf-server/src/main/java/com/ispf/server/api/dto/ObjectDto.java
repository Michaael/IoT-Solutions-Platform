package com.ispf.server.api.dto;

import com.ispf.core.object.PlatformObject;
import com.ispf.server.federation.FederationProxyMetadata;
import com.ispf.core.object.ObjectType;
import com.ispf.server.object.BindingStateVariables;
import com.ispf.server.object.ObjectUiIconService;

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
        String federationRemotePath
) {
    public static ObjectDto from(PlatformObject node) {
        return from(node, null);
    }

    public static ObjectDto from(PlatformObject node, String iconId) {
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
                node.variables().keySet().stream()
                        .filter(name -> !ObjectUiIconService.UI_ICON_VARIABLE.equals(name))
                        .filter(name -> !BindingStateVariables.isReserved(name))
                        .filter(name -> !federated || !FederationProxyMetadata.isFederationVariable(name))
                        .sorted()
                        .toList(),
                node.events().keySet().stream().sorted().toList(),
                federated,
                federated ? FederationProxyMetadata.peerId(node).map(UUID::toString).orElse(null) : null,
                federated ? FederationProxyMetadata.remotePath(node).orElse(null) : null
        );
    }
}
