package com.hemodialyse.backend.domain.stock.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LotTest {

    private Lot newLot(String quantite) {
        return Lot.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                "LOT-1", LocalDate.now().plusMonths(6), new BigDecimal(quantite), new BigDecimal("10"));
    }

    @Test
    void createEnforcesQuantiteInvariant() {
        assertThrows(BusinessException.class, () -> Lot.create(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, "LOT-1", LocalDate.now(), new BigDecimal("-5"), new BigDecimal("10")));
    }

    @Test
    void consommerDecrementsRemaining() {
        Lot lot = newLot("100");
        lot.consommer(new BigDecimal("40"));
        assertEquals(0, new BigDecimal("60").compareTo(lot.getQuantiteRestante()));
    }

    @Test
    void consommerRejectsNonPositive() {
        Lot lot = newLot("100");
        assertThrows(BusinessException.class, () -> lot.consommer(BigDecimal.ZERO));
    }

    @Test
    void consommerRejectsMoreThanRemaining() {
        Lot lot = newLot("30");
        assertThrows(BusinessException.class, () -> lot.consommer(new BigDecimal("31")));
    }

    @Test
    void restituerIncrementsRemaining() {
        Lot lot = newLot("100");
        lot.consommer(new BigDecimal("30"));
        assertEquals(0, new BigDecimal("70").compareTo(lot.getQuantiteRestante()));
        lot.restituer(new BigDecimal("20"));
        assertEquals(0, new BigDecimal("90").compareTo(lot.getQuantiteRestante()));
    }

    @Test
    void restituerRejectsNonPositive() {
        Lot lot = newLot("100");
        assertThrows(BusinessException.class, () -> lot.restituer(BigDecimal.ZERO));
    }

    @Test
    void restituerCanRestoreMoreThanInitial() {
        // The domain allows over-restoration (edge case when correcting data manually)
        Lot lot = newLot("10");
        lot.restituer(new BigDecimal("5"));
        assertEquals(0, new BigDecimal("15").compareTo(lot.getQuantiteRestante()));
    }
}

