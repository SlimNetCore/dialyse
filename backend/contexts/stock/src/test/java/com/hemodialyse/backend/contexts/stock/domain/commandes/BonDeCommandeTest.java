package com.hemodialyse.backend.contexts.stock.domain.commandes;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Règles du bon de commande (RG-STK-005, 010 à 012) : lignes valides, brouillon seul modifiable, validation avec ligne.
 */
class BonDeCommandeTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());

    private static LigneDeBon ligne(String quantite, String prix) {
        return LigneDeBon.saisie(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(quantite),
                prix == null ? null : new BigDecimal(prix));
    }

    private static BonDeCommande brouillon(LigneDeBon... lignes) {
        return BonDeCommande.brouillon(UUID.randomUUID(), CENTRE, "BC-00001", null, "u", OffsetDateTime.now(), List.of(lignes));
    }

    @Test
    void une_ligne_exige_un_article_une_quantite_positive_et_un_prix_non_negatif() {
        assertThrows(BonDeCommandeInvalide.class, () -> LigneDeBon.saisie(null, null, BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(BonDeCommandeInvalide.class, () -> ligne("0", "1"));
        assertThrows(BonDeCommandeInvalide.class, () -> ligne("-2", "1"));
        assertThrows(BonDeCommandeInvalide.class, () -> LigneDeBon.saisie(null, UUID.randomUUID(), null, BigDecimal.ONE));
        assertThrows(BonDeCommandeInvalide.class, () -> ligne("1", "-0.01"));
        ligne("1", null);
        ligne("0.001", "0");
    }

    @Test
    void le_total_somme_les_sous_totaux_arrondis_et_ignore_les_lignes_sans_prix() {
        assertEquals(new BigDecimal("13.33"), brouillon(ligne("10", "1.333"), ligne("3", null)).total());
        assertEquals(BigDecimal.ZERO, brouillon().total());
    }

    @Test
    void les_lignes_ne_se_remplacent_qu_en_brouillon() {
        BonDeCommande bon = brouillon(ligne("1", "1"));
        assertEquals(1, bon.remplacerLesLignes(List.of(ligne("2", "2"))).lignes().get(0).quantite().intValue() - 1);
        BonDeCommande valide = bon.valider();
        assertEquals("BON_COMMANDE_NON_MODIFIABLE",
                assertThrows(RefusDuReferentiel.class, () -> valide.remplacerLesLignes(List.of())).code());
        BonDeCommande recu = BonDeCommande.reconstituer(bon.id(), CENTRE, "BC-00001", null, StatutDuBon.RECU, "u",
                OffsetDateTime.now(), bon.lignes());
        assertEquals("BON_COMMANDE_NON_MODIFIABLE",
                assertThrows(RefusDuReferentiel.class, () -> recu.remplacerLesLignes(List.of())).code());
    }

    @Test
    void la_validation_exige_un_brouillon_avec_au_moins_une_ligne() {
        assertEquals("BON_COMMANDE_SANS_LIGNE", assertThrows(RefusDuReferentiel.class, () -> brouillon().valider()).code());
        BonDeCommande valide = brouillon(ligne("1", "1")).valider();
        assertEquals(StatutDuBon.VALIDE, valide.statut());
        assertEquals("BON_COMMANDE_NON_VALIDABLE", assertThrows(RefusDuReferentiel.class, valide::valider).code());
    }
}

