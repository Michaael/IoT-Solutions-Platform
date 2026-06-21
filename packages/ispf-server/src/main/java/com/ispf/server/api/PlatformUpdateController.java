package com.ispf.server.api;

import com.ispf.server.platform.update.PlatformUpdateService;
import com.ispf.server.platform.update.PlatformUpdateStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/update")
public class PlatformUpdateController {

    private final PlatformUpdateService updateService;

    public PlatformUpdateController(PlatformUpdateService updateService) {
        this.updateService = updateService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return toPayload(updateService.getStatus());
    }

    @PostMapping("/check")
    public Map<String, Object> checkNow() {
        return toPayload(updateService.refreshCheck());
    }

    @PostMapping("/apply")
    public Map<String, Object> apply() {
        try {
            PlatformUpdateStatus result = updateService.applyLatestUpdate();
            Map<String, Object> payload = toPayload(result);
            payload.put("accepted", true);
            payload.put("message", "Обновление запущено. Сервер перезапустится через несколько секунд.");
            return payload;
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage());
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, error.getMessage());
        }
    }

    private static Map<String, Object> toPayload(PlatformUpdateStatus status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("checkEnabled", status.checkEnabled());
        payload.put("applyEnabled", status.applyEnabled());
        payload.put("currentVersion", status.currentVersion());
        payload.put("latestVersion", status.latestVersion());
        payload.put("updateAvailable", status.updateAvailable());
        payload.put("releaseName", status.releaseName());
        payload.put("releaseUrl", status.releaseUrl());
        payload.put("releaseNotes", status.releaseNotes());
        payload.put("publishedAt", status.publishedAt());
        payload.put("checkedAt", status.checkedAt());
        payload.put("checkError", status.checkError());
        payload.put("applyState", status.applyState());
        payload.put("applyMessage", status.applyMessage());
        payload.put("applyStartedAt", status.applyStartedAt());
        return payload;
    }
}
