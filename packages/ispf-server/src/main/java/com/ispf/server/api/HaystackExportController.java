package com.ispf.server.api;

import com.ispf.server.platform.HaystackExportService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/haystack")
public class HaystackExportController {

    private final HaystackExportService haystackExportService;

    public HaystackExportController(HaystackExportService haystackExportService) {
        this.haystackExportService = haystackExportService;
    }

    @GetMapping("/search")
    public Map<String, Object> search(
            Authentication authentication,
            @RequestParam List<String> tags,
            @RequestParam(required = false) String rootPath,
            @RequestParam(defaultValue = "point") String entityKind,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return haystackExportService.searchByTags(rootPath, tags, entityKind, limit);
    }

    @GetMapping("/export")
    public Map<String, Object> export(
            Authentication authentication,
            @RequestParam(required = false) String rootPath,
            @RequestParam(defaultValue = "true") boolean includePoints
    ) {
        return haystackExportService.exportSubtree(rootPath, includePoints);
    }
}
