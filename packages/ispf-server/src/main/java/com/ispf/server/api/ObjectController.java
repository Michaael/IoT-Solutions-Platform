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
import com.ispf.server.api.dto.ObjectEditorDto;
import com.ispf.server.api.dto.VariableDto;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectRevisionConflictException;
import com.ispf.server.object.ObjectEditLeaseService;
import com.ispf.server.api.support.ObjectCollaborationSupport;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectUiIconService;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.driver.DeviceProvisioningService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/objects")
public class ObjectController {

    private final ObjectManager objectManager;
    private final DashboardService dashboardService;
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

    public ObjectController(
            ObjectManager objectManager,
            DashboardService dashboardService,
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
            ObjectEditLeaseService editLeaseService
    ) {
        this.objectManager = objectManager;
        this.dashboardService = dashboardService;
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
    }

    private ObjectDto toDto(PlatformObject node) {
        return ObjectDto.from(node, objectUiIconService.readIconId(node).orElse(null));
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
            Authentication authentication
    ) {
        var tree = objectManager.tree();
        if (parent == null || parent.isBlank()) {
            return tree.all().stream()
                    .filter(node -> tenantScopeService.isPathVisible(node.path(), authentication))
                    .filter(node -> objectAccessService.canRead(node.path(), authentication))
                    .map(this::toDto)
                    .toList();
        }
        if (!tenantScopeService.isPathVisible(parent, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope denied for " + parent);
        }
        objectAccessService.requireRead(parent, authentication);
        return tree.childrenOf(parent).stream()
                .filter(node -> tenantScopeService.isPathVisible(node.path(), authentication))
                .filter(node -> objectAccessService.canRead(node.path(), authentication))
                .map(this::toDto)
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
        if (request.type() == ObjectType.DASHBOARD) {
            dashboardService.ensureDashboardStructure(node.path());
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
        return toDto(objectManager.require(node.path()));
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
            PlatformObject node = objectManager.updateInfo(path, request.displayName(), request.description());
            return toDto(node);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
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
            BindingExpressionValidator.validateOrThrow(request.bindingExpression());
            Variable variable = objectManager.createVariable(
                    path,
                    request.name(),
                    request.schema(),
                    request.readable(),
                    request.writable(),
                    request.bindingExpression(),
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
            if (request.bindingExpression() != null) {
                BindingExpressionValidator.validateOrThrow(request.bindingExpression());
            }
            Variable variable = objectManager.updateVariableDefinition(
                    path,
                    name,
                    request.bindingExpression(),
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
            String bindingExpression,
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
            String bindingExpression,
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
            String driverId,
            Integer driverPollIntervalMs,
            Boolean autoStartDriver
    ) {
    }

    public record UpdateObjectRequest(
            String displayName,
            String description,
            String iconId
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
                target.remotePath()
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
}
