package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase;
import com.hemodialyse.backend.domain.comptabilite.valueobject.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GenerateurEcritureComptableTest {

    private final GenerateurEcritureComptable generateur = new GenerateurEcritureComptable();
    private final UUID centerId = UUID.randomUUID();
    private final MappingComptable mapping = MappingComptable.defaultFor(centerId);

    @Test
    void ecriture_facturation_doit_etre_equilibree() {
        var cmd = new ComptabiliteUseCase.GenererEcritureFacturationCommand(
                centerId, UUID.randomUUID(), "FACT-2026-0001",
                UUID.randomUUID(), UUID.randomUUID(), "CNAS",
                new BigDecimal("1000.00"), BigDecimal.ZERO, new BigDecimal("1000.00"),
                LocalDate.of(2026, 8, 14), "Patient Test CNAS"
        );
        // Exonéré de TVA
        EcritureComptable ecriture = generateur.genererEcritureFacturation(cmd, mapping, "VE-2026-0001", null);

        assertEquals(JournalCode.VE, ecriture.getJournalCode());
        assertEquals(StatutEcriture.VALIDEE, ecriture.getStatut());
        // Débit == Crédit
        assertEquals(ecriture.totalDebit(), cmd.totalTtc());
        // Compte débit = 411200 (CNAS)
        assertEquals("411200", ecriture.getLignes().get(0).getCompteSCF());
        // Compte crédit = 706 (produit)
        assertEquals("706", ecriture.getLignes().get(1).getCompteSCF());
    }

    @Test
    void ecriture_facturation_avec_tva_doit_avoir_3_lignes() {
        var cmd = new ComptabiliteUseCase.GenererEcritureFacturationCommand(
                centerId, UUID.randomUUID(), "FACT-2026-0002",
                UUID.randomUUID(), UUID.randomUUID(), "PATIENT_DIRECT",
                new BigDecimal("1000.00"), new BigDecimal("90.00"), new BigDecimal("1090.00"),
                LocalDate.of(2026, 8, 14), "Patient direct avec TVA"
        );
        RegleTVA regleTVA = new RegleTVA("DIALYSE", new BigDecimal("9.00"), false,
                LocalDate.of(2026, 1, 1), null, "Art 1 loi finances 2026");

        EcritureComptable ecriture = generateur.genererEcritureFacturation(cmd, mapping, "VE-2026-0002", regleTVA);

        assertEquals(3, ecriture.getLignes().size());
        // Équilibre : débit 1090 = crédit 1000 + 90
        assertEquals(new BigDecimal("1090.00"), ecriture.totalDebit());
    }

    @Test
    void ecriture_reglement_doit_etre_equilibree() {
        var cmd = new ComptabiliteUseCase.GenererEcritureReglementCommand(
                centerId, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("500.00"), LocalDate.of(2026, 8, 14),
                "BANQUE", "CNAS Agence Centre", UUID.randomUUID(), "CNAS"
        );
        EcritureComptable ecriture = generateur.genererEcritureReglement(cmd, mapping, "BQ-2026-0001");

        assertEquals(JournalCode.BQ, ecriture.getJournalCode());
        assertEquals(2, ecriture.getLignes().size());
        assertEquals(ecriture.totalDebit(),
                ecriture.getLignes().stream().map(l -> l.getMontantCredit())
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void ecriture_desequilibree_doit_lever_exception() {
        // Deux lignes : débit 100 mais crédit 50 → déséquilibre → BusinessException
        var ligneDebit = com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture.debit(
                "411200", "test débit", new BigDecimal("100.00"), null, java.util.List.of());
        var ligneCredit = com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture.credit(
                "706", "test crédit", new BigDecimal("50.00"), null, java.util.List.of());
        assertThrows(com.hemodialyse.backend.domain.shared.exception.BusinessException.class, () ->
                new EcritureComptable(UUID.randomUUID(), centerId, JournalCode.VE,
                        LocalDate.now(), LocalDate.now(), "VE-001", "test",
                        java.util.List.of(ligneDebit, ligneCredit), StatutEcriture.BROUILLON, UUID.randomUUID())
        );
    }
}


