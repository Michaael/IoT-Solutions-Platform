package com.ispf.server.plugin.model;

import com.ispf.core.object.ObjectType;
import com.ispf.server.api.dto.ObjectDto;
import com.ispf.server.plugin.model.dto.ModelDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/instance-types")
public class InstanceTypeController {

    private final TypedModelFacade facade;

    public InstanceTypeController(TypedModelFacade instanceTypeFacade) {
        this.facade = instanceTypeFacade;
    }

    @GetMapping
    public List<ModelDto> list(
            @RequestParam(required = false) ObjectType platformType,
            @RequestParam(required = false) String parentPath
    ) {
        if (platformType != null || (parentPath != null && !parentPath.isBlank())) {
            return facade.listForCreate(platformType, parentPath);
        }
        return facade.list();
    }

    @GetMapping("/{id}")
    public ModelDto get(@PathVariable String id) {
        return facade.get(id);
    }

    @GetMapping("/by-name/{name}")
    public ModelDto getByName(@PathVariable String name) {
        return facade.getByName(name);
    }

    @PostMapping
    public ModelDto create(@Valid @RequestBody TypedModelFacade.CreatePayload request) {
        return facade.create(request);
    }

    @PutMapping("/{id}")
    public ModelDto update(@PathVariable String id, @Valid @RequestBody TypedModelFacade.UpdatePayload request) {
        return facade.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        facade.delete(id);
    }

    @PostMapping("/{id}/instantiate")
    public ObjectDto instantiate(
            @PathVariable String id,
            @Valid @RequestBody TypedModelFacade.InstantiatePayload request
    ) {
        return facade.instantiate(id, request);
    }
}
