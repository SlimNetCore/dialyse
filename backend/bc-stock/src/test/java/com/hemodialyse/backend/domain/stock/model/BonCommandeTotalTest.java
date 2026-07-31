package com.hemodialyse.backend.domain.stock.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BonCommandeTotalTest {

    @Test
    void totalSumsLineSubtotalsThroughMoneyValueObject() {
        BonCommande bon = BonCommande.brouillon(UUID.randomUUID(), "BL-0001", UUID.randomUUID(), "tester");
        bon.remplacerLignes(List.of(
                new LigneBonCommande(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("3"), new BigDecimal("1.5")),
                new LigneBonCommande(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("2"), new BigDecimal("10.00"))
        ));
        // 3 * 1.5 + 2 * 10.00 = 4.5 + 20.00 = 24.5
        assertEquals(0, new BigDecimal("24.5").compareTo(bon.total()));
    }

    @Test
    void lineWithMissingOperandsContributesZero() {
        BonCommande bon = BonCommande.brouillon(UUID.randomUUID(), "BL-0002", UUID.randomUUID(), "tester");
        bon.remplacerLignes(List.of(
                new LigneBonCommande(UUID.randomUUID(), UUID.randomUUID(), null, new BigDecimal("10")),
                new LigneBonCommande(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("4"), new BigDecimal("2.5"))
        ));
        assertEquals(0, new BigDecimal("10.0").compareTo(bon.total()));
    }

    @Test
    void emptyBonHasZeroTotal() {
        BonCommande bon = BonCommande.brouillon(UUID.randomUUID(), "BL-0003", UUID.randomUUID(), "tester");
        assertEquals(0, BigDecimal.ZERO.compareTo(bon.total()));
    }
}

