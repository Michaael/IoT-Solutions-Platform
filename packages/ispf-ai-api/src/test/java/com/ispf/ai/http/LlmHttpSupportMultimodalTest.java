package com.ispf.ai.http;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ispf.ai.LlmContentPart;
import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmHttpSupportMultimodalTest {

    @Test
    void buildsMultimodalContentArray() {
        LlmRequest request = new LlmRequest(
                "vision-model",
                List.of(new LlmMessage(
                        "user",
                        "describe",
                        List.of(
                                LlmContentPart.text("describe this"),
                                LlmContentPart.imageUrl("data:image/png;base64,abc")
                        )
                )),
                128,
                0.1
        );
        ObjectNode body = LlmHttpSupport.chatCompletionBody(request);
        ArrayNode messages = (ArrayNode) body.get("messages");
        ArrayNode content = (ArrayNode) messages.get(0).get("content");
        assertEquals(2, content.size());
        assertEquals("text", content.get(0).get("type").asText());
        assertEquals("image_url", content.get(1).get("type").asText());
        assertTrue(content.get(1).get("image_url").get("url").asText().startsWith("data:image/png"));
    }
}
