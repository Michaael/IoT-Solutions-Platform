package com.ispf.server.object;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObjectReorderApiTest {

    private static final String PARENT = "root.platform.devices";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reordersDirectChildrenWithinParent() throws Exception {
        String first = mockMvc.perform(get("/api/v1/objects").param("parent", PARENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(1)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        var children = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(first);
        java.util.List<String> paths = new java.util.ArrayList<>();
        for (var child : children) {
            paths.add(child.get("path").asText());
        }
        if (paths.size() < 2) {
            throw new IllegalStateException("Expected at least two device children for reorder test");
        }
        String pathA = paths.get(0);
        String pathB = paths.get(1);
        java.util.List<String> reordered = new java.util.ArrayList<>(paths);
        reordered.set(0, pathB);
        reordered.set(1, pathA);
        String orderedJson = tools.jackson.databind.json.JsonMapper.builder().build()
                .writeValueAsString(reordered);

        mockMvc.perform(put("/api/v1/objects/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "%s",
                                  "orderedPaths": %s
                                }
                                """.formatted(PARENT, orderedJson)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/objects").param("parent", PARENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path").value(pathB))
                .andExpect(jsonPath("$[1].path").value(pathA));
    }
}
