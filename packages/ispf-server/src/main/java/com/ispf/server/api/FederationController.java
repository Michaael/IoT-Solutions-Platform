package com.ispf.server.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.server.federation.FederationCatalogService;
import com.ispf.server.federation.FederationPeer;
import com.ispf.server.federation.FederationPeerDraft;
import com.ispf.server.federation.FederationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/federation")
public class FederationController {

    private final FederationService federationService;
    private final FederationCatalogService federationCatalogService;
    private final ObjectMapper objectMapper;

    public FederationController(
            FederationService federationService,
            FederationCatalogService federationCatalogService,
            ObjectMapper objectMapper
    ) {
        this.federationService = federationService;
        this.federationCatalogService = federationCatalogService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/peers")
    public List<FederationPeerDto> listPeers() {
        return federationService.listPeers().stream().map(FederationPeerDto::from).toList();
    }

    @PostMapping("/peers")
    public FederationPeerDto createPeer(@Valid @RequestBody FederationPeerRequest request) {
        return FederationPeerDto.from(federationService.createPeer(request.toDraft()));
    }

    @PutMapping("/peers/{id}")
    public FederationPeerDto updatePeer(@PathVariable UUID id, @Valid @RequestBody FederationPeerRequest request) {
        return FederationPeerDto.from(federationService.updatePeer(id, request.toDraft()));
    }

    @DeleteMapping("/peers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePeer(@PathVariable UUID id) {
        federationService.deletePeer(id);
    }

    @PostMapping("/peers/{id}/sync-catalog")
    public SyncCatalogResponse syncCatalog(@PathVariable UUID id) {
        FederationCatalogService.SyncResult result = federationCatalogService.syncCatalog(id);
        return new SyncCatalogResponse(result.localRoot(), result.created(), result.updated(), result.remoteCount());
    }

    @GetMapping("/proxy/objects/by-path")
    public Object proxyObject(
            @RequestParam UUID peerId,
            @RequestParam String path
    ) {
        JsonNode json = federationService.proxyObjectByPath(peerId, path);
        return objectMapper.convertValue(json, Object.class);
    }

    @PatchMapping("/proxy/objects/by-path/variables/value")
    public Object proxyVariablePatch(
            @RequestParam UUID peerId,
            @RequestParam String path,
            @RequestParam String name,
            @RequestBody(required = false) Map<String, Object> body
    ) throws tools.jackson.core.JacksonException {
        JsonNode json = federationService.proxyVariablePatch(
                peerId,
                path,
                name,
                objectMapper.writeValueAsString(body != null ? body : Map.of())
        );
        return objectMapper.convertValue(json, Object.class);
    }

    @PostMapping("/proxy/objects/by-path/functions/invoke")
    public Object proxyFunctionInvoke(
            @RequestParam UUID peerId,
            @RequestParam String path,
            @RequestParam String name,
            @RequestBody(required = false) Map<String, Object> body
    ) throws tools.jackson.core.JacksonException {
        JsonNode json = federationService.proxyFunctionInvoke(
                peerId,
                path,
                name,
                objectMapper.writeValueAsString(body != null ? body : Map.of())
        );
        return objectMapper.convertValue(json, Object.class);
    }

    @PostMapping("/remote-token")
    public Map<String, Object> fetchRemoteToken(@Valid @RequestBody RemoteTokenRequest request) {
        return federationService.fetchRemoteLoginToken(request.baseUrl(), request.username(), request.password());
    }

    public record SyncCatalogResponse(
            String localRoot,
            int created,
            int updated,
            int remoteCount
    ) {
    }

    public record FederationPeerDto(
            UUID id,
            String name,
            String baseUrl,
            String pathPrefix,
            boolean enabled,
            String description,
            boolean hasAuthToken
    ) {
        static FederationPeerDto from(FederationPeer peer) {
            return new FederationPeerDto(
                    peer.id(),
                    peer.name(),
                    peer.baseUrl(),
                    peer.pathPrefix(),
                    peer.enabled(),
                    peer.description(),
                    peer.authToken() != null && !peer.authToken().isBlank()
            );
        }
    }

    public record FederationPeerRequest(
            @NotBlank String name,
            @NotBlank String baseUrl,
            String authToken,
            String pathPrefix,
            Boolean enabled,
            String description
    ) {
        FederationPeerDraft toDraft() {
            return new FederationPeerDraft(
                    name,
                    baseUrl,
                    authToken,
                    pathPrefix,
                    enabled == null || enabled,
                    description
            );
        }
    }

    public record RemoteTokenRequest(
            @NotBlank String baseUrl,
            @NotBlank String username,
            @NotBlank String password
    ) {
    }
}
