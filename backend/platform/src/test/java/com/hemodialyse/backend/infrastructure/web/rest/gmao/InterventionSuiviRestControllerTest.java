package com.hemodialyse.backend.infrastructure.web.rest.gmao;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterventionSuiviRestControllerTest {

    @Test
    void zone_should_accept_an_iana_zone_and_fall_back_to_utc_otherwise() {
        assertEquals(ZoneId.of("Africa/Algiers"), InterventionSuiviRestController.zone("Africa/Algiers"));
        assertEquals(ZoneOffset.UTC, InterventionSuiviRestController.zone(null));
        assertEquals(ZoneOffset.UTC, InterventionSuiviRestController.zone("  "));
        assertEquals(ZoneOffset.UTC, InterventionSuiviRestController.zone("Mars/Olympus"));
    }
}
