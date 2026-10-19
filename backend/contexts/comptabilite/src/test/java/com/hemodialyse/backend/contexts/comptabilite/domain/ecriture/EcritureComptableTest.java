package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeCloturee;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeOuverte;
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

    // ------------------------------------------------------------------ fabriques (R7, R9, R10, R21)

    private static final java.time.YearMonth MARS = java.time.YearMonth.of(2026, 3);
    private static final UUID MODELE = UUID.randomUUID();

    private static PeriodeOuverte mars() {
        return PeriodeComptable.ouverte(CENTRE, MARS).exigerOuverte();
    }

    private static EcritureComptable pieceSaisie() {
        UUID id = UUID.randomUUID();
        return EcritureComptable.saisir(id, CENTRE, CodeJournal.VE, MODELE, DATE, NumeroDePiece.de("VE-2026-000001"),
                "Loyer", lignesEquilibrees(), mars());
    }

    @Test
    void une_periode_cloturee_ne_delivre_pas_de_preuve_d_ouverture() {
        PeriodeComptable cloturee = PeriodeComptable.reconstituer(CENTRE, MARS, true, null, null);

        PeriodeCloturee generee = assertThrows(PeriodeCloturee.class, cloturee::exigerOuverte);
        PeriodeCloturee saisie = assertThrows(PeriodeCloturee.class, cloturee::exigerOuverteALaSaisie);

        assertEquals("PERIODE_CLOTUREE", generee.code());
        assertEquals("La période 2026-03 est clôturée pour le centre " + CENTRE.valeur(), generee.getMessage());
        assertEquals("La période 2026-03 est clôturée", saisie.getMessage());
    }

    @Test
    void une_ecriture_generee_est_validee_et_exige_la_periode_de_sa_date() {
        EcritureComptable ecriture = EcritureComptable.generer(UUID.randomUUID(), CENTRE, CodeJournal.VE, DATE, DATE,
                NumeroDePiece.de("VE-2026-000001"), "Fact.", lignesEquilibrees(), UUID.randomUUID(), mars());

        assertEquals(StatutEcriture.VALIDEE, ecriture.statut());
        assertThrows(IllegalArgumentException.class, () -> EcritureComptable.generer(UUID.randomUUID(), CENTRE,
                CodeJournal.VE, DATE.plusMonths(1), DATE, NumeroDePiece.de("VE-2026-000002"), "Fact.",
                lignesEquilibrees(), UUID.randomUUID(), mars()));
    }

    @Test
    void une_piece_saisie_a_deux_lignes_au_moins_et_sa_propre_source() {
        EcritureComptable piece = pieceSaisie();
        assertEquals(piece.id(), piece.source());
        assertEquals(MODELE, piece.modele());

        PieceIncomplete refus = assertThrows(PieceIncomplete.class, () -> EcritureComptable.saisir(UUID.randomUUID(),
                CENTRE, CodeJournal.VE, MODELE, DATE, NumeroDePiece.de("VE-2026-000002"), "Loyer",
                List.of(ligne("411", "10.00", "0.00")), mars()));
        assertEquals("PIECE_MONTANTS_INVALIDES", refus.code());
    }

    @Test
    void l_extourne_inverse_les_lignes_d_une_piece_saisie_et_ne_s_extourne_pas_elle_meme() {
        EcritureComptable piece = pieceSaisie();
        UUID source = UUID.randomUUID();

        EcritureComptable extourne = piece.extourner(UUID.randomUUID(), DATE, NumeroDePiece.de("VE-2026-000002"), source,
                UUID::randomUUID, mars());

        assertEquals("Extourne VE-2026-000001 - Loyer", extourne.libelle());
        assertEquals(source, extourne.source());
        assertEquals(new BigDecimal("10.00"), extourne.lignes().get(0).credit());
        assertEquals(new BigDecimal("10.00"), extourne.lignes().get(1).debit());
        assertEquals(PieceDejaExtournee.class, assertThrows(RuntimeException.class, extourne::exigerExtournable).getClass());
    }

    @Test
    void une_ecriture_generee_par_le_systeme_ne_s_extourne_pas() {
        EcritureComptable systeme = ecriture(lignesEquilibrees(), StatutEcriture.VALIDEE);

        PieceNonSaisie refus = assertThrows(PieceNonSaisie.class, systeme::exigerExtournable);
        assertEquals("PIECE_NON_SAISIE", refus.code());
    }

    @Test
    void la_revision_des_sorties_garde_l_identite_et_refuse_une_ecriture_exportee() {
        EcritureComptable sorties = ecriture(lignesEquilibrees(), StatutEcriture.VALIDEE);
        List<LigneEcriture> nouvelles = List.of(ligne("602", "25.00", "0.00"), ligne("322", "0.00", "25.00"));

        EcritureComptable revisee = sorties.reviserLesSorties(nouvelles, mars());
        assertEquals(sorties.id(), revisee.id());
        assertEquals(sorties.numeroDePiece(), revisee.numeroDePiece());
        assertEquals(new BigDecimal("25.00"), revisee.totalDebit());

        sorties.marquerExportee(java.time.Instant.parse("2026-03-31T12:00:00Z"), "comptable");
        assertThrows(EcritureExporteeImmuable.class, () -> sorties.reviserLesSorties(nouvelles, mars()));
    }
}


