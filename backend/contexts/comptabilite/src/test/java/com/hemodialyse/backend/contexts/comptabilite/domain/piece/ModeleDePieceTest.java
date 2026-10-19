package com.hemodialyse.backend.contexts.comptabilite.domain.piece;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.LigneEcriture;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModeleDePieceTest {

    private static final ModeleDePiece LOYER = new ModeleDePiece(UUID.randomUUID(), new CentreId(UUID.randomUUID()),
            "LOYER", "Loyer", CodeJournal.VE, true, List.of(
            new ModeleDePiece.Ligne(ModeleDePiece.Sens.DEBIT, "613", null),
            new ModeleDePiece.Ligne(ModeleDePiece.Sens.DEBIT, "44566", "TVA déductible"),
            new ModeleDePiece.Ligne(ModeleDePiece.Sens.CREDIT, "401", null)));

    private static List<BigDecimal> montants(String... valeurs) {
        return java.util.Arrays.stream(valeurs).map(BigDecimal::new).toList();
    }

    @Test
    void compose_une_ligne_par_montant_non_nul_dans_le_sens_du_modele() {
        List<LigneEcriture> lignes = LOYER.composerLaPiece(montants("100", "0", "100"), "Loyer mars", List.of(),
                UUID::randomUUID);

        assertEquals(2, lignes.size());
        assertEquals("613", lignes.get(0).compte());
        assertEquals("Loyer mars", lignes.get(0).libelle());
        assertEquals(0, lignes.get(0).debit().compareTo(new BigDecimal("100")));
        assertEquals("401", lignes.get(1).compte());
        assertEquals(0, lignes.get(1).credit().compareTo(new BigDecimal("100")));
    }

    @Test
    void refuse_un_montant_negatif_ou_un_nombre_de_montants_different_des_lignes() {
        ModeleDePiece.ReglePiece negatif = assertThrows(ModeleDePiece.ReglePiece.class,
                () -> LOYER.composerLaPiece(montants("100", "-1", "99"), "Loyer", List.of(), UUID::randomUUID));
        assertEquals("PIECE_MONTANTS_INVALIDES", negatif.code());

        assertThrows(ModeleDePiece.ReglePiece.class,
                () -> LOYER.composerLaPiece(montants("100", "100"), "Loyer", List.of(), UUID::randomUUID));
    }
}

