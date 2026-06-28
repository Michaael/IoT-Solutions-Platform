package com.ispf.server.api.dto;

import com.ispf.core.object.PlatformObject;
import com.ispf.server.federation.FederationProxyMetadata;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.VisualGroupConstants;
import com.ispf.server.object.BindingStateVariables;
import com.ispf.server.object.ObjectUiIconService;
import com.ispf.server.plugin.model.dto.AppliedModelDto;
import com.ispf.plugin.model.SystemIntrinsicModels;

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
        List<AppliedModelDto> appliedModels,
        boolean groupRef,
        String groupContextPath,
        boolean groupMemberMissing,
        /** DEVICE only: driverStatus variable (STOPPED, RUNNING, ERROR). */
        String driverStatus,
        /** DEVICE only: live connected flag when status is RUNNING. */
        Boolean driverConnected,
        /** When true, CEL/SQL binding invocations for this object are written to binding_invoke_audit. */
        boolean bindingAuditEnabled,
        /** When true, function invocations for this object are written to function_invoke_audit. */
        boolean functionAuditEnabled
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
                sanitizeTemplateId(node.templateId().orElse(null)),
                iconId,
                node.createdAt(),
                node.sortOrder(),
                node.revision(),
                node.lastChangedBy(),
                node.lastChangedAt(),
                lite ? List.of() : node.variables().keySet().stream()
                        .filter(name -> !ObjectUiIconService.UI_ICON_VARIABLE.equals(name))
                        .filter(name -> !BindingStateVariables.isReserved(name))
                        .filter(name -> !VisualGroupConstants.isReservedVariable(name))
                        .filter(name -> !federated || !FederationProxyMetadata.isFederationVariable(name))
                        .sorted()
                        .toList(),
                lite ? List.of() : node.events().keySet().stream().sorted().toList(),
                federated,
                federated ? FederationProxyMetadata.peerId(node).map(UUID::toString).orElse(null) : null,
                federated ? FederationProxyMetadata.remotePath(node).orElse(null) : null,
                appliedModels != null ? appliedModels : List.of(),
                false,
                null,
                false,
                null,
                null,
                node.bindingAuditEnabled(),
                node.functionAuditEnabled()
        );
    }

    public ObjectDto withDriverRuntime(String driverStatus, Boolean driverConnected) {
        return new ObjectDto(
                id,
                path,
                type,
                displayName,
                description,
                templateId,
                iconId,
                createdAt,
                sortOrder,
                revision,
                lastChangedBy,
                lastChangedAt,
                variableNames,
                eventNames,
                federated,
                federationPeerId,
                federationRemotePath,
                appliedModels,
                groupRef,
                groupContextPath,
                groupMemberMissing,
                driverStatus,
                driverConnected,
                bindingAuditEnabled,
                functionAuditEnabled
        );
    }

    private static String sanitizeTemplateId(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return null;
        }
        if (SystemIntrinsicModels.isIntrinsicName(templateId)) {
            return null;
        }
        return templateId;
    }

    public static ObjectDto asGroupMember(
            PlatformObject member,
            String groupContextPath,
            int sortOrder,
            String iconId,
            boolean missing
    ) {
        return new ObjectDto(
                member.id(),
                member.path(),
                member.type(),
                missing ? "(отсутствует) " + member.path() : member.displayName(),
                member.description(),
                sanitizeTemplateId(member.templateId().orElse(null)),
                iconId,
                member.createdAt(),
                sortOrder,
                member.revision(),
                member.lastChangedBy(),
                member.lastChangedAt(),
                List.of(),
                List.of(),
                false,
                null,
                null,
                List.of(),
                true,
                groupContextPath,
                missing,
                null,
                null,
                member.bindingAuditEnabled(),
                member.functionAuditEnabled()
        );
    }

    public static ObjectDto missingGroupMember(String memberPath, String groupContextPath, int sortOrder) {
        return new ObjectDto(
                memberPath,
                memberPath,
                ObjectType.CUSTOM,
                "(отсутствует) " + memberPath,
                "",
                null,
                null,
                null,
                sortOrder,
                0L,
                null,
                null,
                List.of(),
                List.of(),
                false,
                null,
                null,
                List.of(),
                true,
                groupContextPath,
                true,
                null,
                null,
                false,
                false
        );
    }
}
