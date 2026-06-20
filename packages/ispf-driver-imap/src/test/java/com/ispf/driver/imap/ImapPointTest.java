package com.ispf.driver.imap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImapPointTest {

    @Test
    void parsesMessageCount() {
        ImapPoint point = ImapPoint.parse("messageCount");
        assertEquals(ImapPoint.Kind.MESSAGE_COUNT, point.kind());
    }

    @Test
    void parsesUnseen() {
        ImapPoint point = ImapPoint.parse("UNSEEN");
        assertEquals(ImapPoint.Kind.UNSEEN_COUNT, point.kind());
    }

    @Test
    void parsesSubjectWithNumber() {
        ImapPoint point = ImapPoint.parse("subject:3");
        assertEquals(ImapPoint.Kind.SUBJECT, point.kind());
        assertEquals(3, point.messageNumber());
    }

    @Test
    void rejectsUnknownMapping() {
        assertThrows(IllegalArgumentException.class, () -> ImapPoint.parse("body:1"));
    }
}
