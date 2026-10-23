package com.hemodialyse.backend.contexts.stock.application.commandes;

import com.hemodialyse.backend.contexts.stock.application.commandes.GestionDesBonsDeCommande.LigneSaisie;
import com.hemodialyse.backend.contexts.stock.application.referentiel.ReferentielDesArticles;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonDeCommande;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonDeCommandeInvalide;
import com.hemodialyse.backend.contexts.stock.domain.commandes.StatutDuBon;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Fournisseur;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ReferentielSimple.Fournisseurs;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import com.hemodialyse.backend.contexts.stock.doubles.ArticlesEnMemoire;
import com.hemodialyse.backend.contexts.stock.doubles.BonsEnMemoire;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cas d'usage des bons de commande (RG-STK-005, 010 à 012) : isolation de centre, séquence, brouillon, validation.
 */
class GestionDesBonsDeCommandeTest {

    private static final CentreId A = new CentreId(UUID.randomUUID());
    private static final CentreId B = new CentreId(UUID.randomUUID());
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-10-23T08:00:00Z"), ZoneOffset.UTC);

    private final ArticlesEnMemoire articles = new ArticlesEnMemoire();
    private final ReferentielDesArticles referentiel = new ReferentielDesArticles(articles, UUID::randomUUID, HORLOGE);
    private final BonsEnMemoire repertoire = new BonsEnMemoire();
    private final List<UUID> fournisseursConnus = new ArrayList<>();
    private final GestionDesBonsDeCommande bons = new GestionDesBonsDeCommande(repertoire, articles, new Fournisseurs() {
        @Override
        public void enregistrer(Fournisseur fournisseur) {
        }

        @Override
        public List<Fournisseur> actifs(CentreId centre) {
            return List.of();
        }

        @Override
        public List<Fournisseur> chercher(CentreId centre, String texte) {
            return List.of();
        }

        @Override
        public boolean existe(CentreId centre, UUID id) {
            return fournisseursConnus.contains(id) && centre.equals(A);
        }
    }, UUID::randomUUID, HORLOGE);

    private static LigneSaisie ligne(UUID article, String quantite, String prix) {
        return new LigneSaisie(null, article, new BigDecimal(quantite), prix == null ? null : new BigDecimal(prix));
    }

    private static String code(Runnable r) {
        return assertThrows(RefusDuReferentiel.class, r::run).code();
    }

    private UUID article(CentreId centre, String code) {
        return referentiel.referencer(centre, FicheArticle.saisie(code, "L " + code, null, null, null, null, "piece", null, null,
                null, null, null, null, null, null, null, false, false, null, false, false, null, null, null)).id();
    }

    @Test
    void la_creation_donne_un_brouillon_reference_par_centre_avec_l_auteur_de_la_session() {
        UUID a1 = article(A, "A1");
        UUID b1 = article(B, "B1");
        BonDeCommande premier = bons.creer(A, null, "pharma", List.of(ligne(a1, "2", "3")));
        assertEquals("BC-00001", premier.reference());
        assertEquals("BC-00002", bons.creer(A, null, "pharma", List.of()).reference());
        assertEquals("BC-00001", bons.creer(B, null, null, List.of(ligne(b1, "1", "1"))).reference());
        assertEquals(StatutDuBon.BROUILLON, premier.statut());
        assertEquals("pharma", premier.creePar());
        assertEquals("system", bons.lister(B).get(0).creePar());
    }

    @Test
    void un_refus_ne_consomme_aucune_reference() {
        UUID a1 = article(A, "A1");
        assertThrows(BonDeCommandeInvalide.class, () -> bons.creer(A, null, "u", List.of(ligne(a1, "-1", "1"))));
        assertEquals("BON_COMMANDE_ARTICLE_INCONNU",
                code(() -> bons.creer(A, null, "u", List.of(ligne(UUID.randomUUID(), "1", "1")))));
        assertEquals("BON_COMMANDE_FOURNISSEUR_INCONNU", code(() -> bons.creer(A, UUID.randomUUID(), "u", List.of())));
        assertEquals(0, repertoire.sequence(A));
        assertEquals("BC-00001", bons.creer(A, null, "u", List.of()).reference());
    }

    @Test
    void les_articles_d_un_autre_centre_et_les_bons_d_un_autre_centre_sont_refuses() {
        UUID b1 = article(B, "B1");
        assertEquals("BON_COMMANDE_ARTICLE_INCONNU", code(() -> bons.creer(A, null, "u", List.of(ligne(b1, "1", "1")))));
        UUID bonA = bons.creer(A, null, "u", List.of()).id();
        assertEquals("BON_COMMANDE_INTROUVABLE", code(() -> bons.consulter(B, bonA)));
        assertEquals("BON_COMMANDE_INTROUVABLE", code(() -> bons.valider(B, bonA)));
        assertEquals("BON_COMMANDE_INTROUVABLE", code(() -> bons.remplacerLesLignes(B, bonA, List.of())));
    }

    @Test
    void le_remplacement_et_la_validation_suivent_le_cycle_du_brouillon() {
        UUID a1 = article(A, "A1");
        UUID bon = bons.creer(A, null, "u", List.of()).id();
        assertEquals("BON_COMMANDE_SANS_LIGNE", code(() -> bons.valider(A, bon)));
        assertEquals(1, bons.remplacerLesLignes(A, bon, List.of(ligne(a1, "5", "4"))).lignes().size());
        assertEquals(StatutDuBon.VALIDE, bons.valider(A, bon).statut());
        assertEquals("BON_COMMANDE_NON_MODIFIABLE", code(() -> bons.remplacerLesLignes(A, bon, List.of(ligne(a1, "1", "1")))));
        assertEquals("BON_COMMANDE_NON_VALIDABLE", code(() -> bons.valider(A, bon)));
        assertEquals(1, bons.consulter(A, bon).lignes().size());
    }
}


