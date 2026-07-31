package com.hemodialyse.backend.domain.seance.vo;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TensionArterielleTest {

    @Test
    void parsesAndNormalizes() {
        TensionArterielle ta = TensionArterielle.parse(" 140 / 90 ");
        assertEquals(140, ta.systolique());
        assertEquals(90, ta.diastolique());
        assertEquals("140/90", ta.format());
    }

    @Test
    void rejectsInvalidFormat() {
        assertThrows(BusinessException.class, () -> TensionArterielle.parse("140-90"));
        assertThrows(BusinessException.class, () -> TensionArterielle.parse("abc/90"));
    }

    @Test
    void rejectsOutOfRange() {
        assertThrows(BusinessException.class, () -> TensionArterielle.parse("500/90"));
        assertThrows(BusinessException.class, () -> TensionArterielle.parse("140/5"));
    }

    @Test
    void rejectsSystolicBelowDiastolic() {
        assertThrows(BusinessException.class, () -> TensionArterielle.parse("80/120"));
    }

    @Test
    void tryParseIsLenientOnBlank() {
        assertTrue(TensionArterielle.tryParse(null).isEmpty());
        assertTrue(TensionArterielle.tryParse("  ").isEmpty());
        Optional<TensionArterielle> parsed = TensionArterielle.tryParse("130/80");
        assertTrue(parsed.isPresent());
        assertEquals("130/80", parsed.get().format());
    }
}

