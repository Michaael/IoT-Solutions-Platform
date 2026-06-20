package com.ispf.driver.ldap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LdapPointTest {

    @Test
    void parsesLdapFilter() {
        LdapPoint point = LdapPoint.parse("(objectClass=*)");
        assertEquals(LdapPoint.Kind.FILTER_COUNT, point.kind());
        assertEquals("(objectClass=*)", point.filter());
        assertNull(point.attribute());
    }

    @Test
    void parsesAttributePath() {
        LdapPoint point = LdapPoint.parse("cn=admin:mail");
        assertEquals(LdapPoint.Kind.ATTRIBUTE, point.kind());
        assertEquals("(cn=admin)", point.filter());
        assertEquals("mail", point.attribute());
    }

    @Test
    void rejectsBlankMapping() {
        assertThrows(IllegalArgumentException.class, () -> LdapPoint.parse("  "));
    }
}
