package com.ispf.driver.webtransaction;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebTransactionStepsTest {

    @Test
    void parsesPipeSeparatedSteps() {
        List<WebTransactionStep> steps = WebTransactionSteps.parseMapping(
                "step1:GET:http://localhost/a|step2:POST:http://localhost/b:payload");
        assertEquals(2, steps.size());
        assertEquals("step1", steps.get(0).name());
        assertEquals("GET", steps.get(0).method());
        assertEquals("http://localhost/a", steps.get(0).url());
        assertEquals("step2", steps.get(1).name());
        assertEquals("POST", steps.get(1).method());
        assertEquals("payload", steps.get(1).body());
    }

    @Test
    void parsesStepsJson() {
        List<WebTransactionStep> steps = WebTransactionSteps.parseMapping(
                "[{\"name\":\"login\",\"method\":\"POST\",\"url\":\"http://localhost/login\",\"body\":\"user=admin\"}]");
        assertEquals(1, steps.size());
        assertEquals("login", steps.get(0).name());
        assertEquals("POST", steps.get(0).method());
        assertEquals("user=admin", steps.get(0).body());
    }

    @Test
    void rejectsBlankMapping() {
        assertThrows(IllegalArgumentException.class, () -> WebTransactionSteps.parseMapping(" "));
    }
}
