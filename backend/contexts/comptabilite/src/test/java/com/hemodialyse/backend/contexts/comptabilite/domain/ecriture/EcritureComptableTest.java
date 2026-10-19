package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EcritureComptableTest {
    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);

    private static List<LigneEcriture> lignesEquilibrees() {
        return List.of(ligne("411", "10.00", "0.00"), ligne("706", "0.00", "10.00"));
    }

    private static LigneEcriture ligne(String compte, String debit, String credit) {
        return new LigneEcriture(UUID.randomUUID(), compte, compte, new BigDecimal(debit), new BigDecimal(credit), null,
                List.of());
    }

    private static EcritureComptable ecriture(List<LigneEcriture> lignes, StatutEcriture statut) {
        return EcritureComptable.reconstituer(UUID.randomUUID(), CENTRE, CodeJournal.VE, DATE, DATE,
                NumeroDePiece.de("VE-2026-000001"), "Test", lignes, statut, null, null, null, null);
    }

    @Test
    void reconstitution_refuse_une_ecriture_desequilibree() {
        assertThrows(EcritureDesequilibree.class, () -> ecriture(List.of(
                ligne("411", "10.00", "0.00"), ligne("706", "0.00", "9.99")), StatutEcriture.BROUILLON));
    }

    @Test
    void validation_est_idempotente_mais_une_ecriture_exportee_est_immuable() {
        EcritureComptable ecriture = ecriture(lignesEquilibrees(), StatutEcriture.BROUILLON);
        ecriture.valider();
        ecriture.valider();
        assertEquals(StatutEcriture.VALIDEE, ecriture.statut());

        ecriture.marquerExportee(java.time.Instant.parse("2026-03-10T12:00:00Z"), "comptable");
        assertEquals(StatutEcriture.EXPORTEE, ecriture.statut());
        assertThrows(EcritureExporteeImmuable.class, ecriture::valider);
    }
}
