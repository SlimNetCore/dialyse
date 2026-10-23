package com.hemodialyse.backend.contexts.stock.domain.sortieseances;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sortie pour séances (T40) : écart = consommé − sorti par lot, excédent en ligne sans lot, motif exigé pour tout écart, pièce figée
 * une fois rapprochée.
 */
class SortiePourSeancesTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-10-23T08:00:00Z");
    private static final UUID ARTICLE = UUID.randomUUID();
    private static final UUID LOT1 = UUID.randomUUID();
    private static final UUID LOT2 = UUID.randomUUID();
    private static final UUID L1 = UUID.randomUUID();
    private static final UUID L2 = UUID.randomUUID();

    private static BigDecimal d(String valeur) {
        return new BigDecimal(valeur);
    }

    private static SortiePourSeances piece() {
        return SortiePourSeances.enregistrer(UUID.randomUUID(), CENTRE, "SPS-00001", LocalDate.of(2026, 10, 24), PerimetreDeSortie.journee(),
                T0, "gestionnaire", List.of(LigneDePiece.sortie(L1, ARTICLE, LOT1, d("10"), d("5.00")),
                        LigneDePiece.sortie(L2, ARTICLE, LOT2, d("4"), d("6.00"))));
    }

    @Test
    void une_piece_sans_ligne_est_refusee() {
        RefusDuReferentiel refus = assertThrows(RefusDuReferentiel.class, () -> SortiePourSeances.enregistrer(UUID.randomUUID(), CENTRE,
                "SPS-1", LocalDate.now(), PerimetreDeSortie.journee(), T0, "u", List.of()));
        assertEquals("PIECE_SANS_LIGNE", refus.code());
    }

    @Test
    void l_ecart_est_le_consomme_moins_le_sorti_et_l_exces_devient_une_ligne_sans_lot() {
        SortiePourSeances constatee = piece().constater(Map.of(LOT1, d("10"), LOT2, d("1")), Map.of(ARTICLE, d("3")), UUID::randomUUID);
        LigneDePiece l1 = constatee.lignes().get(0);
        LigneDePiece l2 = constatee.lignes().get(1);
        LigneDePiece exces = constatee.lignes().get(2);
        assertEquals(0, d("0").compareTo(l1.ecart()));
        assertEquals(0, d("-3").compareTo(l2.ecart()));
        assertNull(exces.lotId());
        assertEquals(0, d("3").compareTo(exces.ecart()));
        assertEquals(0, d("0").compareTo(exces.quantiteSortie()));
    }

    @Test
    void un_ecart_sans_motif_empeche_le_rapprochement_et_un_motif_le_permet() {
        SortiePourSeances constatee = piece().constater(Map.of(LOT1, d("10")), Map.of(), UUID::randomUUID);
        assertEquals("ECART_SANS_MOTIF", assertThrows(RefusDuReferentiel.class, constatee::exigerRapprochable).code());
        SortiePourSeances justifiee = constatee.justifier(L2, "  non utilise  ");
        assertEquals("non utilise", justifiee.lignes().get(1).motif());
        SortiePourSeances rapprochee = justifiee.rapprocher("directeur", T0.plusDays(1));
        assertTrue(rapprochee.estRapprochee());
        assertEquals("directeur", rapprochee.valideePar());
    }

    @Test
    void un_motif_vide_ou_une_ligne_inconnue_est_refuse() {
        SortiePourSeances piece = piece();
        assertEquals("ECART_SANS_MOTIF", assertThrows(RefusDuReferentiel.class, () -> piece.justifier(L1, " ")).code());
        assertEquals("PIECE_LIGNE_INCONNUE", assertThrows(RefusDuReferentiel.class, () -> piece.justifier(UUID.randomUUID(), "m")).code());
    }

    @Test
    void une_piece_rapprochee_est_figee_et_le_motif_disparait_avec_l_ecart() {
        SortiePourSeances justifiee = piece().constater(Map.of(LOT1, d("10")), Map.of(), UUID::randomUUID).justifier(L2, "retour");
        SortiePourSeances apresConsommation = justifiee.constater(Map.of(LOT1, d("10"), LOT2, d("4")), Map.of(), UUID::randomUUID);
        assertNull(apresConsommation.lignes().get(1).motif());
        SortiePourSeances rapprochee = apresConsommation.rapprocher("u", T0);
        assertEquals("PIECE_DEJA_RAPPROCHEE", assertThrows(RefusDuReferentiel.class, () -> rapprochee.constater(Map.of(), Map.of(),
                UUID::randomUUID)).code());
        assertEquals("PIECE_DEJA_RAPPROCHEE", assertThrows(RefusDuReferentiel.class, () -> rapprochee.justifier(L1, "m")).code());
    }

    @Test
    void le_motif_d_un_exces_est_conserve_quand_l_exces_est_reconstate() {
        SortiePourSeances avecExces = piece().constater(Map.of(LOT1, d("10"), LOT2, d("4")), Map.of(ARTICLE, d("2")), UUID::randomUUID);
        UUID ligneExces = avecExces.lignes().get(2).id();
        SortiePourSeances recomptee = avecExces.justifier(ligneExces, "dose supplementaire")
                .constater(Map.of(LOT1, d("10"), LOT2, d("4")), Map.of(ARTICLE, d("5")), UUID::randomUUID);
        assertEquals("dose supplementaire", recomptee.lignes().get(2).motif());
        assertEquals(0, d("5").compareTo(recomptee.lignes().get(2).ecart()));
    }
}

