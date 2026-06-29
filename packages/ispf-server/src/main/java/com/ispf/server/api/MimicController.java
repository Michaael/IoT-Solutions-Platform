package com.ispf.server.api;

import com.ispf.server.mimic.MimicService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mimics")
public class MimicController {

    private final MimicService mimicService;

    public MimicController(MimicService mimicService) {
        this.mimicService = mimicService;
    }

    @GetMapping("/by-path")
    public MimicService.MimicView get(@RequestParam String path) {
        return mimicService.getMimic(path);
    }

    @PutMapping("/by-path/diagram")
    public MimicService.MimicView saveDiagram(
            @RequestParam String path,
            @RequestBody SaveDiagramRequest request
    ) {
        return mimicService.saveDiagram(path, request.diagramJson());
    }

    @PutMapping("/by-path/title")
    public MimicService.MimicView saveTitle(
            @RequestParam String path,
            @RequestBody SaveTitleRequest request
    ) {
        return mimicService.updateTitle(path, request.title());
    }

    public record SaveDiagramRequest(@NotBlank String diagramJson) {
    }

    public record SaveTitleRequest(@NotBlank String title) {
    }
}
