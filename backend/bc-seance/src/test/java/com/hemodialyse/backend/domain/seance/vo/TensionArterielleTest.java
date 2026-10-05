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
    void convertsCentimetresOfMercuryToMillimetres() {
        assertEquals("110/80", TensionArterielle.parse("11/8").format());
        assertEquals("140/90", TensionArterielle.parse(" 14 / 9 ").format());
        assertEquals("125/75", TensionArterielle.parse("12,5/7.5").format());
        assertEquals(110, TensionArterielle.parse("11/8").systolique());
    }

    @Test
    void keepsMillimetresUntouchedAndStillValidatesCentimetreInput() {
        assertEquals("120/80", TensionArterielle.parse("120/80").format());
        assertThrows(BusinessException.class, () -> TensionArterielle.parse("8/12"));
        assertThrows(BusinessException.class, () -> TensionArterielle.parse("1/0,5"));
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

