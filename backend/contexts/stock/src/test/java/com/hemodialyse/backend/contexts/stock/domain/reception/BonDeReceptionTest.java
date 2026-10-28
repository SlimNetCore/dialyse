package com.hemodialyse.backend.contexts.stock.domain.reception;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonDeCommandeInvalide;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Règles du bon de réception (RG-STK-020 à 025) : lignes valides dès la saisie, brouillon puis validation, lot rattaché à la ligne.
 */
class BonDeReceptionTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final LocalDate JOUR = LocalDate.parse("2026-10-20");

    private static LigneDeReception ligne(String quantite, String prix) {
        return LigneDeReception.saisie(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(quantite),
                prix == null ? null : new BigDecimal(prix), null, null, null);
    }

    private static BonDeReception brouillon(LigneDeReception... lignes) {
        return BonDeReception.brouillon(UUID.randomUUID(), CENTRE, "BR-00001", null, null, JOUR, "u", OffsetDateTime.now(), List.of(lignes));
    }

    @Test
    void une_ligne_exige_un_article_une_quantite_positive_et_un_prix_non_negatif() {
        assertThrows(BonDeCommandeInvalide.class,
                () -> LigneDeReception.saisie(null, null, BigDecimal.ONE, BigDecimal.ONE, null, null, null));
        assertThrows(BonDeCommandeInvalide.class, () -> ligne("0", "1"));
        assertThrows(BonDeCommandeInvalide.class, () -> ligne("-1", "1"));
        assertThrows(BonDeCommandeInvalide.class, () -> ligne("1", "-0.5"));
        assertTrue(ligne("1", null).lotId() == null);
    }

    @Test
    void la_validation_rattache_chaque_ligne_a_son_lot_et_ne_se_fait_qu_une_fois() {
        BonDeReception bon = brouillon(ligne("5", "2"));
        UUID lot = UUID.randomUUID();
        BonDeReception valide = bon.valider(bon.lignes().stream().map(l -> l.avecLot(lot)).toList());
        assertEquals(BonDeReception.Statut.VALIDE, valide.statut());
        assertEquals(lot, valide.lignes().get(0).lotId());
        assertEquals("RECEPTION_NON_VALIDABLE", assertThrows(RefusDuReferentiel.class, () -> valide.valider(valide.lignes())).code());
    }

    @Test
    void un_bon_sans_ligne_ne_se_valide_pas() {
        assertEquals("RECEPTION_SANS_LIGNE", assertThrows(RefusDuReferentiel.class, () -> brouillon().exigerValidable()).code());
    }

    @Test
    void le_brouillon_garde_ce_qui_est_omis_et_un_bon_valide_ne_se_modifie_plus_comme_un_brouillon() {
        UUID fournisseur = UUID.randomUUID();
        BonDeReception bon = BonDeReception.brouillon(UUID.randomUUID(), CENTRE, "BR-00001", null, fournisseur, JOUR, "u",
                OffsetDateTime.now(), List.of(ligne("1", "1")));
        BonDeReception modifie = bon.modifierLeBrouillon(null, null, List.of());
        assertEquals(fournisseur, modifie.fournisseurId());
        assertEquals(JOUR, modifie.dateReception());
        assertTrue(modifie.lignes().isEmpty());
        BonDeReception valide = BonDeReception.reconstituer(bon.id(), CENTRE, "BR-00001", null, fournisseur, JOUR,
                BonDeReception.Statut.VALIDE, "u", OffsetDateTime.now(), bon.lignes());
        assertEquals("RECEPTION_DEJA_VALIDEE",
                assertThrows(RefusDuReferentiel.class, () -> valide.modifierLeBrouillon(null, null, List.of())).code());
        assertEquals(JOUR.minusDays(1), valide.corriger(null, JOUR.minusDays(1), valide.lignes()).dateReception());
    }
}

