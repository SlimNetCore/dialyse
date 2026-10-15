package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase;
import com.hemodialyse.backend.domain.comptabilite.valueobject.ComptesStock;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.MappingComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.OperationComptable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Le mapping comptable est tenu par le contexte comptabilite (slice C2) : le choix du journal de chaque opération et ses
 * contrôles (journal inconnu, désactivé) y sont testés. Reste ici l'effet sur la génération des écritures.
 */
class JournauxDuMappingTest {

    private static final UUID CENTRE = UUID.randomUUID();
    private static final JournalCode OD = JournalCode.de("OD");

    private final ComptabiliteFixtures.Monde monde = new ComptabiliteFixtures.Monde();
    private final ComptabiliteService comptabilite = monde.comptabilite;

    @Test
    void changing_the_journal_of_an_operation_never_duplicates_an_entry_already_posted() {
        UUID facture = UUID.randomUUID();
        var commande = new ComptabiliteUseCase.GenererEcritureFacturationCommand(CENTRE, facture, "FACT-2",
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"), BigDecimal.ZERO,
                new BigDecimal("100.00"), LocalDate.of(2026, 10, 5), "Séance");
        EcritureComptable premiere = comptabilite.genererEcritureFacturation(commande);

        monde.mappings.save(new MappingComptable(CENTRE, "706", "411100", "411500", "512", "530", "44571",
                ComptesStock.parDefaut(), Map.of(OperationComptable.VENTE, OD)));
        EcritureComptable rejouee = comptabilite.genererEcritureFacturation(commande);

        assertEquals(premiere.getId(), rejouee.getId());
        assertEquals(1, monde.ecritures.du(CENTRE).size());
    }
}
