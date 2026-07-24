package com.hemodialyse.backend.infrastructure.web.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AuthCookieConfigIntegrationTest {

    @Autowired
    private AuthRestController controller;

    @Test
    void application_defaults_should_be_localhost_compatible() {
        assertFalse((Boolean) ReflectionTestUtils.getField(controller, "authCookieSecure"));
        assertEquals("Lax", ReflectionTestUtils.getField(controller, "authCookieSameSite"));
        assertEquals("HEMO_AUTH", ReflectionTestUtils.getField(controller, "authCookieName"));
        assertEquals("HEMO_REFRESH", ReflectionTestUtils.getField(controller, "refreshCookieName"));
    }
}

