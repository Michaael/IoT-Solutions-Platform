package com.ispf.server.api;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.server.federation.FederationBindService;
import com.ispf.server.federation.FederationProxyService;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.expression.BindingExpressionValidator;
import com.ispf.server.api.dto.ObjectDto;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.plugin.model.ModelApplicationService;
import com.ispf.server.plugin.model.dto.AppliedModelDto;
import com.ispf.server.api.dto.ObjectEditorDto;
import com.ispf.server.api.dto.VariableDto;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectRevisionConflictException;
import com.ispf.server.object.ObjectEditLeaseService;
import com.ispf.server.api.support.ObjectCollaborationSupport;
import com.ispf.server.object.ObjectBulkDeleteService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTreeDriverEnricher;
import com.ispf.server.object.VisualGroupService;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.object.ObjectUiIconService;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.report.ReportService;
import com.ispf.server.driver.DeviceProvisioningService;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.security.PlatformRoleService;
import com.ispf.server.security.PlatformUserService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.workflow.WorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/objects")
public class ObjectController {

    private final ObjectManager objectManager;
    private final ObjectTemplateService objectTemplateService;
    private final DashboardService dashboardService;
    private final ReportService reportService;
    private final WorkflowService workflowService;
    private final PlatformUserService platformUserService;
    private final PlatformRoleService platformRoleService;
    private final ObjectUiIconService objectUiIconService;
    private final DeviceProvisioningService deviceProvisioningService;
    private final AutomationTreeService automationTreeService;
    private final ObjectAccessService objectAccessService;
    private final TenantScopeService tenantScopeService;
    private final FederationProxyService federationProxyService;
    private final FederationBindService federationBindService;
    private final ObjectMapper objectMapper;
    private final ObjectEditLeaseService editLeaseService;
    private final ModelRegistry modelRegistry;
    private final ModelApplicationService modelApplicationService;
    private final VisualGroupService visualGroupService;
    private final ObjectBulkDeleteService objectBulkDeleteService;
    private final DriverRuntimeService driverRuntimeService;

    public ObjectController(
            ObjectManager objectManager,
            ObjectTemplateService objectTemplateService,
            DashboardService dashboardService,
            ReportService reportService,
            WorkflowService workflowService,
            PlatformUserService platformUserService,
            PlatformRoleService platformRoleService,
            ObjectUiIconService objectUiIconService,
            DeviceProvisioningService deviceProvisioningService,
            AutomationTreeService automationTreeService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            FederationProxyService federationProxyService,
            FederationBindService federationBindService,
            ObjectMapper objectMapper,
            ObjectEditLeaseService editLeaseService,
            ModelRegistry modelRegistry,
            ModelApplicationService modelApplicationService,
            VisualGroupService visualGroupService,
            ObjectBulkDeleteService objectBulkDeleteService,
            DriverRuntimeService driverRuntimeService
    ) {
        this.objectManager = objectManager;
        this.objectTemplateService = objectTemplateService;
        this.dashboardService = dashboardService;
        this.reportService = reportService;
        this.workflowService = workflowService;
        this.platformUserService = platformUserService;
        this.platformRoleService = platformRoleService;
        this.objectUiIconService = objectUiIconService;
        this.deviceProvisioningService = deviceProvisioningService;
        this.automationTreeService = automationTreeService;
        this.objectAccessService = objectAccessService;
        this.tenantScopeService = tenantScopeService;
        this.federationProxyService = federationProxyService;
        this.federationBindService = federationBindService;
        this.objectMapper = objectMapper;
        this.editLeaseService = editLeaseService;
        this.modelRegistry = modelRegistry;
        this.modelApplicationService = modelApplicationService;
        this.visualGroupService = visualGroupService;
        this.objectBulkDeleteService = objectBulkDeleteService;
        this.driverRuntimeService = driverRuntimeService;
    }

    private ObjectDto toDto(PlatformObject node) {
        return ObjectDto.from(
                node,
                objectUiIconService.readIconId(node).orElse(null),
                AppliedModelDto.resolve(node, modelRegistry)
        );
    }

    private ObjectDto toLiteDto(PlatformObject node) {
        ObjectDto dto = ObjectDto.fromLite(
                node,
                objectUiIconService.readIconId(node).orElse(null),
                AppliedModelDto.resolve(node, modelRegistry)
        );
        return ObjectTreeDriverEnricher.enrichLite(dto, node, driverRuntimeService);
    }

    private void beginWrite(String path, Authentication authentication, HttpHeaders headers) {
        objectAccessService.requireWrite(path, authentication);
        editLeaseService.assertWritable(path, authentication != null ? authentication.getName() : "system");
        ObjectCollaborationSupport.bindWriteContext(authentication, headers);
    }

    private void endWrite() {
        ObjectCollaborationSupport.clearContext();
    }

    @GetMapping("/by-path/audit")
    public List<Map<String, Object>> audit(
            @RequestParam String path,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication
    ) {
        objectAccessService.requireRead(path, authentication);
        return objectManager.configAudit(path, limit).stream()
                .map(entry -> Map.<String, Object>of(
                        "id", entry.id(),
                        "objectPath", entry.objectPath(),
                        "changeType", entry.changeType(),
                        "field", entry.field() != null ? entry.field() : "",
                        "actor", entry.actor() != null ? entry.actor() : "",
                        "occurredAt", entry.occurredAt().toString(),
                        "revisionBefore", entry.revisionBefore(),
                        "revisionAfter", entry.revisionAfter(),
                        "summaryJson", entry.summaryJson() != null ? entry.summaryJson() : ""
                ))
                .toList();
    }

    @GetMapping("/leases")
    public List<ObjectEditLeaseService.EditLease> listLeases(Authentication authentication) {
        objectAccessService.requireAdmin(authentication);
        return editLeaseService.listActive();
    }

    @PostMapping("/leases")
    public ObjectEditLeaseService.EditLease acquireLease(
            @Valid @RequestBody AcquireLeaseRequest request,
            Authentication authentication
    ) {
        objectAccessService.requireAdmin(authentication);
        Duration ttl = request.ttlMinutes() != null
                ? Duration.ofMinutes(request.ttlMinutes())
                : Duration.ofHours(2);
        return editLeaseService.acquire(
                request.pathPrefix(),
                authentication.getName(),
                ttl
        );
    }

    @DeleteMapping("/leases")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseLease(
            @RequestParam String pathPrefix,
            Authentication authentication
    ) {
        objectAccessService.requireAdmin(authentication);
        editLeaseService.release(pathPrefix, authentication.getName());
    }

    public record AcquireLeaseRequest(
            @NotBlank String pathPrefix,
            Integer ttlMinutes
    ) {
    }

    @GetMapping
    public List<ObjectDto> list(
            @RequestParam(required = false) String parent,
            @RequestParam(defaultValue = "false") boolean lite,
            Authentication authentication
    ) {
        var tree = objectManager.tree();
        java.util.function.Function<PlatformObject, ObjectDto> mapper = lite
                ? this::toLiteDto
                : this::toDto;
        if (parent == null || parent.isBlank()) {
            return tree.all().stream()
                    .filter(node -> tenantScopeService.isPathVisible(node.path(), authentication))
                    .filter(node -> objectAccessService.canRead(node.path(), authentication))
                    .map(mapper)
                    .toList();
        }
        if (!tenantScopeService.isPathVisible(parent, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope denied for " + parent);
        }
        objectAccessService.requireRead(parent, authentication);
        PlatformObject parentNode = objectManager.require(parent);
        if (parentNode.type() == ObjectType.VISUAL_GROUP) {
            return resolveVisualGroupMembers(parent, authentication, lite);
        }
        return tree.childrenOf(parent).stream()
                .filter(node -> tenantScopeService.isPathVisible(node.path(), authentication))
                .filter(node -> objectAccessService.canRead(node.path(), authentication))
                .filter(node -> !visualGroupService.isHiddenFromStructuralTree(node.path()))
                .map(mapper)
                .toList();
    }

    @GetMapping("/by-path/editor")
    public ObjectEditorDto editor(@RequestParam String path, Authentication authentication) {
        if (!tenantScopeService.isPathVisible(path, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope denied for " + path);
        }
        objectAccessService.requireRead(path, authentication);
        return ObjectEditorDto.from(objectManager.require(path), objectUiIconService);
    }

    @GetMapping("/by-path")
    public ObjectDto get(@RequestParam String path, Authentication authentication) {
        if (!tenantScopeService.isPathVisible(path, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope denied for " + path);
        }
        objectAccessService.requireRead(path, authentication);
        return federationProxyService.resolve(path)
                .map(target -> mapProxyObject(path, target))
                .orElseGet(() -> toDto(objectManager.require(path)));
    }

    @PostMapping
    public ObjectDto create(@Valid @RequestBody CreateObjectRequest request, Authentication authentication) {
        objectAccessService.requireWrite(request.parentPath(), authentication);
        federationBindService.assertParentAllowsChildren(request.parentPath());
        String fullPath = objectManager.tree().resolveChildPath(request.parentPath(), request.name());
        if (objectManager.tree().findByPath(fullPath).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Object exists: " + fullPath);
        }
        PlatformObject node = objectManager.create(
                request.parentPath(),
                request.name(),
                request.type(),
                request.displayName(),
                request.description(),
                request.templateId()
        );
        objectTemplateService.applyTemplate(node.path(), request.templateId());
        boolean autoApplyRelative = request.autoApplyRelativeModels() == null
                || Boolean.TRUE.equals(request.autoApplyRelativeModels());
        if (autoApplyRelative) {
            modelApplicationService.applyRelativeModelsWithRules(node.path());
        }
        // #region agent log
        if (request.type() == ObjectType.DEVICE) {
            agentLogCreate("ObjectController.java:create:afterRelativeModels", "after applyRelativeModelsWithRules", "H1", Map.of(
                    "path", node.path(),
                    "requestedDriverId", request.driverId() != null ? request.driverId() : "(null)",
                    "autoApplyRelative", autoApplyRelative,
                    "existingDriverId", driverRuntimeService.debugReadDriverId(node.path()).orElse("(none)")
            ));
        }
        // #endregion
        if (request.type() == ObjectType.DASHBOARD) {
            dashboardService.ensureDashboardStructure(node.path());
        }
        if (request.type() == ObjectType.REPORT) {
            reportService.ensureReportStructure(node.path());
        }
        if (request.type() == ObjectType.WORKFLOW) {
            workflowService.ensureWorkflowStructure(node.path());
        }
        if (request.type() == ObjectType.ALERT) {
            automationTreeService.ensureAlertRuleStructure(node.path());
        }
        if (request.type() == ObjectType.CORRELATOR) {
            automationTreeService.ensureCorrelatorStructure(node.path());
        }
        if (request.type() == ObjectType.DEVICE && request.driverId() != null && !request.driverId().isBlank()) {
            deviceProvisioningService.provisionDriver(
                    node.path(),
                    request.driverId(),
                    request.driverPollIntervalMs(),
                    Boolean.TRUE.equals(request.autoStartDriver())
            );
            objectManager.persistNodeTree(node.path());
        }
        return toDto(node);
    }

    @PatchMapping("/by-path")
    public ObjectDto update(
            @RequestParam String path,
            @Valid @RequestBody UpdateObjectRequest request,
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader HttpHeaders headers
    ) {
        beginWrite(path, authentication, headers);
        try {
            if (request.iconId() != null) {
                objectUiIconService.setIconId(path, request.iconId());
            }
            PlatformObject node = objectManager.require(path);
            if (request.displayName() != null || request.description() != null) {
                node = objectManager.updateInfo(
                        path,
                        request.displayName() != null ? request.displayName() : node.displayName(),
                        request.description() != null ? request.description() : node.description()
                );
            }
            if (request.bindingAuditEnabled() != null) {
                node = objectManager.updateBindingAuditEnabled(path, request.bindingAuditEnabled());
            }
            if (request.functionAuditEnabled() != null) {
                node = objectManager.updateFunctionAuditEnabled(path, request.functionAuditEnabled());
            }
            return toDto(node);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
    }

    @PostMapping("/bulk-delete")
    public ObjectBulkDeleteService.BulkDeleteResult bulkDelete(
            @Valid @RequestBody BulkDeleteRequest request,
            Authentication authentication
    ) {
        if (request.paths() == null || request.paths().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paths is required");
        }
        for (String path : request.paths()) {
            if (path != null && !path.isBlank()) {
                objectAccessService.requireWrite(path.trim(), authentication);
            }
        }
        return objectBulkDeleteService.deleteAll(request.paths());
    }

    public record BulkDeleteRequest(@NotEmpty List<@NotBlank String> paths) {
    }

    private List<ObjectDto> resolveVisualGroupMembers(
            String groupPath,
            Authentication authentication,
            boolean lite
    ) {
        List<ObjectDto> result = new ArrayList<>();
        for (var member : visualGroupService.listMembers(groupPath)) {
            var optionalNode = objectManager.tree().findByPath(member.path());
            if (optionalNode.isEmpty()) {
                result.add(ObjectDto.missingGroupMember(member.path(), groupPath, member.sortOrder()));
                continue;
            }
            PlatformObject node = optionalNode.get();
            if (!tenantScopeService.isPathVisible(node.path(), authentication)
                    || !objectAccessService.canRead(node.path(), authentication)) {
                continue;
            }
            String iconId = objectUiIconService.readIconId(node).orElse(null);
            result.add(ObjectDto.asGroupMember(node, groupPath, member.sortOrder(), iconId, false));
        }
        return result;
    }

    @DeleteMapping("/by-path")
    public void delete(@RequestParam String path, Authentication authentication) {
        objectAccessService.requireWrite(path, authentication);
        try {
            if (platformUserService.isSecurityUserPath(path)) {
                platformUserService.deleteUser(platformUserService.usernameFromPath(path));
                return;
            }
            if (platformRoleService.isSecurityRolePath(path)) {
                platformRoleService.deleteRole(platformRoleService.roleNameFromPath(path));
                return;
            }
            if (objectManager.tree().findByPath(path)
                    .filter(node -> node.type() == ObjectType.CORRELATOR)
                    .isPresent()) {
                automationTreeService.deleteCorrelator(path);
                return;
            }
            objectManager.delete(path);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (ObjectNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PutMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@Valid @RequestBody ReorderObjectsRequest request, Authentication authentication) {
        objectAccessService.requireWrite(request.parentPath(), authentication);
        try {
            objectManager.reorderChildren(request.parentPath(), request.orderedPaths());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (ObjectNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/by-path/variables")
    public List<VariableDto> listVariables(@RequestParam String path, Authentication authentication) {
        if (!tenantScopeService.isPathVisible(path, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope denied for " + path);
        }
        objectAccessService.requireRead(path, authentication);
        var proxy = federationProxyService.resolve(path);
        if (proxy.isPresent()) {
            JsonNode json = federationProxyService.proxyVariables(proxy.get());
            return objectMapper.convertValue(json, new TypeReference<List<VariableDto>>() { });
        }
        PlatformObject node = objectManager.require(path);
        return node.variables().values().stream().map(VariableDto::from).toList();
    }

    @GetMapping("/variables/batch")
    public Map<String, List<VariableDto>> listVariablesBatch(
            @RequestParam String paths,
            Authentication authentication
    ) {
        String[] pathArray = paths.split(",");
        if (pathArray.length > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Max 50 paths per batch request");
        }
        Map<String, List<VariableDto>> result = new LinkedHashMap<>();
        for (String rawPath : pathArray) {
            String path = rawPath.trim();
            if (path.isBlank()) {
                continue;
            }
            if (!tenantScopeService.isPathVisible(path, authentication)) {
                continue;
            }
            if (!objectAccessService.canRead(path, authentication)) {
                continue;
            }
            try {
                var proxy = federationProxyService.resolve(path);
                if (proxy.isPresent()) {
                    JsonNode json = federationProxyService.proxyVariables(proxy.get());
                    result.put(path, objectMapper.convertValue(json, new TypeReference<List<VariableDto>>() { }));
                } else {
                    PlatformObject node = objectManager.require(path);
                    result.put(path, node.variables().values().stream().map(VariableDto::from).toList());
                }
            } catch (ObjectNotFoundException e) {
                // omit paths that do not exist
            }
        }
        return result;
    }

    @GetMapping("/by-path/variables/detail")
    public VariableDto getVariable(
            @RequestParam String path,
            @RequestParam String name,
            Authentication authentication
    ) {
        objectAccessService.requireRead(path, authentication);
        PlatformObject node = objectManager.require(path);
        Variable variable = node.getVariable(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variable: " + name));
        return VariableDto.from(variable);
    }

    @PutMapping("/by-path/variables")
    public VariableDto setVariable(
            @RequestParam String path,
            @RequestParam String name,
            @RequestBody DataRecord value,
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader HttpHeaders headers
    ) {
        beginWrite(path, authentication, headers);
        try {
            var proxy = federationProxyService.resolve(path);
            if (proxy.isPresent()) {
                try {
                    JsonNode json = federationProxyService.proxyVariablePut(
                            proxy.get(),
                            name,
                            objectMapper.writeValueAsString(value)
                    );
                    return objectMapper.convertValue(json, VariableDto.class);
                } catch (tools.jackson.core.JacksonException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
                }
            }
            Variable variable = objectManager.setVariableValue(path, name, value);
            if (platformUserService.isSecurityUserPath(path)) {
                platformUserService.syncVariableFromObject(path, name, value);
            }
            return VariableDto.from(variable);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
    }

    @PatchMapping("/by-path/variables/history")
    public VariableDto updateVariableHistory(
            @RequestParam String path,
            @RequestParam String name,
            @Valid @RequestBody UpdateVariableHistoryRequest request,
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader HttpHeaders headers
    ) {
        beginWrite(path, authentication, headers);
        try {
            Variable variable = objectManager.updateVariableHistory(
                    path,
                    name,
                    request.historyEnabled(),
                    request.historyRetentionDays()
            );
            return VariableDto.from(variable);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
    }

    @PostMapping("/by-path/variables")
    public VariableDto createVariable(
            @RequestParam String path,
            @Valid @RequestBody CreateVariableRequest request,
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader HttpHeaders headers
    ) {
        beginWrite(path, authentication, headers);
        try {
            assertNotFederationBound(path);
            Variable variable = objectManager.createVariable(
                    path,
                    request.name(),
                    request.schema(),
                    request.readable(),
                    request.writable(),
                    request.initialValue(),
                    request.historyEnabled(),
                    request.historyRetentionDays()
            );
            return VariableDto.from(variable);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
    }

    @PatchMapping("/by-path/variables")
    public VariableDto updateVariableDefinition(
            @RequestParam String path,
            @RequestParam String name,
            @Valid @RequestBody UpdateVariableDefinitionRequest request,
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader HttpHeaders headers
    ) {
        beginWrite(path, authentication, headers);
        try {
            Variable variable = objectManager.updateVariableDefinition(
                    path,
                    name,
                    request.readable(),
                    request.writable()
            );
            return VariableDto.from(variable);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
    }

    @DeleteMapping("/by-path/variables")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVariable(
            @RequestParam String path,
            @RequestParam String name,
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader HttpHeaders headers
    ) {
        beginWrite(path, authentication, headers);
        try {
            objectManager.deleteVariable(path, name);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
    }

    @PutMapping("/by-path/functions")
    public FunctionDescriptor upsertFunction(
            @RequestParam String path,
            @Valid @RequestBody FunctionDescriptor function,
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader HttpHeaders headers
    ) {
        beginWrite(path, authentication, headers);
        try {
            return objectManager.upsertFunction(path, function);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
    }

    @DeleteMapping("/by-path/functions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFunction(
            @RequestParam String path,
            @RequestParam String name,
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader HttpHeaders headers
    ) {
        beginWrite(path, authentication, headers);
        try {
            objectManager.deleteFunction(path, name);
        } finally {
            endWrite();
        }
    }

    @PutMapping("/by-path/events")
    public EventDescriptor upsertEvent(
            @RequestParam String path,
            @Valid @RequestBody EventDescriptor event,
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader HttpHeaders headers
    ) {
        beginWrite(path, authentication, headers);
        try {
            return objectManager.upsertEvent(path, event);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
    }

    @DeleteMapping("/by-path/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(
            @RequestParam String path,
            @RequestParam String name,
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader HttpHeaders headers
    ) {
        beginWrite(path, authentication, headers);
        try {
            objectManager.deleteEvent(path, name);
        } finally {
            endWrite();
        }
    }

    public record CreateVariableRequest(
            @NotBlank String name,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord initialValue,
            boolean historyEnabled,
            Integer historyRetentionDays
    ) {
        public CreateVariableRequest {
            if (schema == null) {
                schema = DataSchema.builder(name != null ? name : "value")
                        .field("value", com.ispf.core.model.FieldType.STRING)
                        .build();
            }
        }
    }

    public record UpdateVariableDefinitionRequest(
            Boolean readable,
            Boolean writable
    ) {
    }

    public record UpdateVariableHistoryRequest(
            boolean historyEnabled,
            Integer historyRetentionDays
    ) {
    }

    public record CreateObjectRequest(
            @NotBlank String parentPath,
            @NotBlank String name,
            ObjectType type,
            String displayName,
            String description,
            String templateId,
            Boolean autoApplyRelativeModels,
            String driverId,
            Integer driverPollIntervalMs,
            Boolean autoStartDriver
    ) {
    }

    public record UpdateObjectRequest(
            String displayName,
            String description,
            String iconId,
            Boolean bindingAuditEnabled,
            Boolean functionAuditEnabled
    ) {
    }

    public record ReorderObjectsRequest(
            @NotBlank String parentPath,
            @NotEmpty List<@NotBlank String> orderedPaths
    ) {
    }

    private ObjectDto mapProxyObject(String localPath, FederationProxyService.FederationProxyTarget target) {
        JsonNode json = federationProxyService.proxyObject(target);
        ObjectDto remote = objectMapper.convertValue(json, ObjectDto.class);
        return new ObjectDto(
                remote.id(),
                localPath,
                remote.type(),
                remote.displayName(),
                remote.description(),
                remote.templateId(),
                remote.iconId(),
                remote.createdAt(),
                remote.sortOrder(),
                remote.revision(),
                remote.lastChangedBy(),
                remote.lastChangedAt(),
                remote.variableNames(),
                remote.eventNames(),
                true,
                target.peerId().toString(),
                target.remotePath(),
                remote.appliedModels() != null ? remote.appliedModels() : List.of(),
                false,
                null,
                false,
                remote.driverStatus(),
                remote.driverConnected(),
                remote.bindingAuditEnabled(),
                remote.functionAuditEnabled()
        );
    }

    private void assertNotFederationBound(String path) {
        if (federationProxyService.resolve(path).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Federation-bound objects use remote variables: " + path
            );
        }
    }

    // #region agent log
    private static void agentLogCreate(String location, String message, String hypothesisId, Map<String, Object> data) {
        try {
            String json = "{\"sessionId\":\"c91425\",\"timestamp\":" + System.currentTimeMillis()
                    + ",\"location\":\"" + location + "\",\"message\":\"" + message + "\",\"hypothesisId\":\""
                    + hypothesisId + "\",\"data\":" + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data) + "}\n";
            for (String rel : new String[]{"debug-c91425.log", "../../debug-c91425.log", "../../../debug-c91425.log"}) {
                Path p = Path.of(rel);
                if (Files.exists(p.getParent() == null ? Path.of(".") : p.getParent()) || rel.equals("debug-c91425.log")) {
                    Files.writeString(p, json, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    break;
                }
            }
        } catch (Exception ignored) {
        }
    }
    // #endregion
}
