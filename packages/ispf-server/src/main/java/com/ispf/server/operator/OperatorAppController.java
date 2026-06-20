package com.ispf.server.operator;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operator-apps")
public class OperatorAppController {

    private final OperatorAppUiService service;

    public OperatorAppController(OperatorAppUiService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> listApps() {
        return service.listApps();
    }

    @GetMapping("/{appId}/ui")
    public Map<String, Object> getUi(@PathVariable String appId) {
        try {
            return service.getUi(appId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    @PostMapping("/{appId}")
    public Map<String, Object> createApp(
            @PathVariable String appId,
            @RequestBody CreateOperatorAppRequest request
    ) {
        try {
            return service.createApp(appId, request.title());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    @PutMapping("/{appId}/ui")
    public Map<String, Object> saveUi(@PathVariable String appId, @RequestBody SaveOperatorUiRequest request) {
        try {
            return service.saveUi(appId, request.title(), request.defaultDashboard(), request.dashboards());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    public record CreateOperatorAppRequest(String title) {
    }

    public record SaveOperatorUiRequest(
            String title,
            String defaultDashboard,
            List<Map<String, String>> dashboards
    ) {
    }
}
