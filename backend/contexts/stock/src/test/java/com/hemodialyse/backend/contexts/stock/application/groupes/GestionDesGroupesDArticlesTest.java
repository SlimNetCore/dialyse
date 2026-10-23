package com.hemodialyse.backend.contexts.stock.application.groupes;

import com.hemodialyse.backend.contexts.stock.application.referentiel.ReferentielDesArticles;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.groupes.GroupeDArticles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import com.hemodialyse.backend.contexts.stock.doubles.ArticlesEnMemoire;
import com.hemodialyse.backend.contexts.stock.doubles.GroupesDArticlesEnMemoire;
import com.hemodialyse.backend.contexts.stock.interfaces.catalogue.GroupesDArticlesDesCentresDuStock;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.GroupesDArticlesDesCentres;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cas d'usage des groupes d'articles (RG-STK-070 à 073).
 */
class GestionDesGroupesDArticlesTest {

    private static final CentreId A = new CentreId(UUID.randomUUID());
    private static final CentreId B = new CentreId(UUID.randomUUID());

    private final ArticlesEnMemoire articles = new ArticlesEnMemoire();
    private final ReferentielDesArticles referentiel =
            new ReferentielDesArticles(articles, UUID::randomUUID, Clock.fixed(Instant.parse("2026-10-23T08:00:00Z"), ZoneOffset.UTC));
    private final GestionDesGroupesDArticles groupes = new GestionDesGroupesDArticles(new GroupesDArticlesEnMemoire(), articles,
            UUID::randomUUID, Clock.fixed(Instant.parse("2026-10-23T08:00:00Z"), ZoneOffset.UTC));

    private static String code(Runnable r) {
        return assertThrows(RefusDuReferentiel.class, r::run).code();
    }

    private UUID article(CentreId centre, String code) {
        return referentiel.referencer(centre, FicheArticle.saisie(code, "L " + code, null, null, null, null, "piece", null, null,
                null, null, null, null, null, null, null, false, false, null, false, false, null, null, null)).id();
    }

    @Test
    void le_nom_est_unique_par_centre_selon_sa_cle() {
        UUID a1 = article(A, "A1");
        UUID b1 = article(B, "B1");
        groupes.creer(A, "Kit CNAS", null, List.of(a1));
        assertEquals("GROUPE_ARTICLE_NOM_EXISTANT", code(() -> groupes.creer(A, "KÏT  cnas", null, List.of(a1))));
        groupes.creer(B, "Kit CNAS", null, List.of(b1));
        assertEquals(1, groupes.lister(A, 0, 20).total());
    }

    @Test
    void un_groupe_ne_contient_que_des_articles_du_centre() {
        UUID a1 = article(A, "A1");
        UUID b1 = article(B, "B1");
        assertEquals("GROUPE_ARTICLE_ARTICLE_INCONNU", code(() -> groupes.creer(A, "K", null, List.of(a1, b1))));
        assertEquals("GROUPE_ARTICLE_ARTICLE_INCONNU", code(() -> groupes.creer(A, "K", null, List.of(UUID.randomUUID()))));
        assertEquals(0, groupes.lister(A, 0, 20).total());
    }

    @Test
    void la_modification_garde_son_nom_mais_pas_celui_d_un_autre() {
        UUID a1 = article(A, "A1");
        GroupeDArticles g = groupes.creer(A, "Kit", null, List.of(a1));
        groupes.creer(A, "Autre", null, List.of(a1));
        assertEquals("KIT", groupes.modifier(A, g.id(), "KIT", null, List.of(a1)).nom());
        assertEquals("GROUPE_ARTICLE_NOM_EXISTANT", code(() -> groupes.modifier(A, g.id(), "autre", null, List.of(a1))));
    }

    @Test
    void un_groupe_inconnu_ou_d_un_autre_centre_est_introuvable() {
        UUID b1 = article(B, "B1");
        GroupeDArticles gb = groupes.creer(B, "K", null, List.of(b1));
        assertEquals("GROUPE_ARTICLE_INTROUVABLE", code(() -> groupes.consulter(A, gb.id())));
        assertEquals("GROUPE_ARTICLE_INTROUVABLE", code(() -> groupes.modifier(A, gb.id(), "X", null, List.of(b1))));
        assertEquals("GROUPE_ARTICLE_INTROUVABLE", code(() -> groupes.supprimer(A, gb.id())));
    }

    @Test
    void la_suppression_retire_le_groupe() {
        UUID a1 = article(A, "A1");
        GroupeDArticles g = groupes.creer(A, "K", null, List.of(a1));
        groupes.supprimer(A, g.id());
        assertEquals("GROUPE_ARTICLE_INTROUVABLE", code(() -> groupes.consulter(A, g.id())));
    }

    @Test
    void la_liste_est_triee_par_cle_et_paginee() {
        UUID a1 = article(A, "A1");
        for (String nom : List.of("zeta", "Alpha", "élan", "Beta")) {
            groupes.creer(A, nom, null, List.of(a1));
        }
        assertEquals(List.of("élan", "zeta"), groupes.lister(A, 1, 2).groupes().stream().map(GroupeDArticles::nom).toList());
        assertEquals(4, groupes.lister(A, 1, 2).total());
    }

    @Test
    void le_langage_publie_donne_les_groupes_des_centres_demandes_avec_leur_cle() {
        UUID a1 = article(A, "A1");
        UUID b1 = article(B, "B1");
        groupes.creer(A, "Kit CNAS", null, List.of(a1));
        groupes.creer(B, "KIT  cnas", null, List.of(b1));
        GroupesDArticlesDesCentres publie = new GroupesDArticlesDesCentresDuStock(groupes);
        var lus = publie.groupes(List.of(A.valeur()));
        assertEquals(1, lus.size());
        assertEquals("kit cnas", lus.get(0).cle());
        assertEquals(Set.of(a1), lus.get(0).articles());
        assertEquals(2, publie.groupes(List.of(A.valeur(), B.valeur())).size());
        assertTrue(publie.groupes(List.of()).isEmpty());
    }
}

