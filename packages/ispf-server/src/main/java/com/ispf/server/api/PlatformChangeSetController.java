package com.ispf.server.api;

import com.ispf.server.platform.PlatformChangeSetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/change-sets")
public class PlatformChangeSetController {

    private final PlatformChangeSetService changeSetService;

    public PlatformChangeSetController(PlatformChangeSetService changeSetService) {
        this.changeSetService = changeSetService;
    }

    @GetMapping
    public List<PlatformChangeSetService.ChangeSetSummary> list(
            @RequestParam(required = false) String status
    ) {
        return changeSetService.list(status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformChangeSetService.ChangeSet create(
            @Valid @RequestBody CreateChangeSetRequest request,
            Authentication authentication
    ) {
        return changeSetService.create(
                request.title(),
                authentication != null ? authentication.getName() : "system",
                request.ops()
        );
    }

    @GetMapping("/{id}")
    public PlatformChangeSetService.ChangeSet get(@PathVariable String id) {
        return changeSetService.require(id);
    }

    @PostMapping("/{id}/preview")
    public Map<String, Object> preview(@PathVariable String id) {
        return changeSetService.preview(id);
    }

    @PostMapping("/{id}/apply")
    public Map<String, Object> apply(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return changeSetService.apply(id, force);
    }

    public record CreateChangeSetRequest(
            @NotBlank String title,
            List<PlatformChangeSetService.ChangeOp> ops
    ) {
    }
}
