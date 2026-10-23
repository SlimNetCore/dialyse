package com.hemodialyse.backend.contexts.stock.application.referentiel;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ArticleIntrouvable;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.TypeTraitementAnemie;
import com.hemodialyse.backend.contexts.stock.doubles.ArticlesEnMemoire;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cas d'usage du catalogue d'articles (RG-STK-001, 002, 007).
 */
class ReferentielDesArticlesTest {

    private static final CentreId A = new CentreId(UUID.randomUUID());
    private static final CentreId B = new CentreId(UUID.randomUUID());
    private static final Instant MAINTENANT = Instant.parse("2026-10-23T08:00:00Z");

    private final ArticlesEnMemoire articles = new ArticlesEnMemoire();
    private final ReferentielDesArticles referentiel =
            new ReferentielDesArticles(articles, UUID::randomUUID, Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    private static FicheArticle fiche(String code, TypeTraitementAnemie type) {
        return FicheArticle.saisie(code, "Libelle " + code, null, null, null, null, "piece", null, null, null, null, null,
                null, null, null, null, false, false, null, false, false, type, null, null);
    }

    private static FicheArticle fiche(String code) {
        return fiche(code, null);
    }

    @Test
    void un_article_reference_nait_actif_stock_et_pmp_a_zero_horodate() {
        Article a = referentiel.referencer(A, fiche("EPO"));
        assertTrue(a.actif());
        assertEquals(BigDecimal.ZERO, a.stock());
        assertEquals(BigDecimal.ZERO, a.pmp());
        assertEquals(OffsetDateTime.ofInstant(MAINTENANT, ZoneOffset.UTC), a.creeLe());
        assertEquals(a, referentiel.consulter(A, a.id()));
    }

    @Test
    void le_code_est_unique_par_centre_sans_tenir_compte_de_la_casse() {
        referentiel.referencer(A, fiche("ABC"));
        assertEquals("ARTICLE_CODE_EXISTANT",
                assertThrows(RefusDuReferentiel.class, () -> referentiel.referencer(A, fiche("abc"))).code());
        referentiel.referencer(B, fiche("ABC"));
        assertEquals(1, referentiel.lister(A).size());
    }

    @Test
    void la_modification_garde_son_propre_code_mais_refuse_celui_d_un_autre() {
        Article a = referentiel.referencer(A, fiche("AAA"));
        referentiel.referencer(A, fiche("BBB"));
        assertEquals("aaa", referentiel.modifierFiche(A, a.id(), fiche("aaa")).fiche().code());
        assertEquals("ARTICLE_CODE_EXISTANT",
                assertThrows(RefusDuReferentiel.class, () -> referentiel.modifierFiche(A, a.id(), fiche("bbb"))).code());
    }

    @Test
    void un_article_inconnu_ou_d_un_autre_centre_est_introuvable() {
        Article b = referentiel.referencer(B, fiche("B1"));
        assertThrows(ArticleIntrouvable.class, () -> referentiel.consulter(A, UUID.randomUUID()));
        assertThrows(ArticleIntrouvable.class, () -> referentiel.consulter(A, b.id()));
        assertThrows(ArticleIntrouvable.class, () -> referentiel.modifierFiche(A, b.id(), fiche("X")));
        assertThrows(ArticleIntrouvable.class, () -> referentiel.changerActivite(A, b.id(), false));
    }

    @Test
    void l_activite_se_change_sans_toucher_a_la_fiche() {
        Article a = referentiel.referencer(A, fiche("A1"));
        assertFalse(referentiel.changerActivite(A, a.id(), false).actif());
        assertTrue(referentiel.changerActivite(A, a.id(), true).actif());
        assertEquals("A1", referentiel.consulter(A, a.id()).fiche().code());
    }

    @Test
    void listes_triees_par_code_et_filtre_par_type_de_traitement() {
        referentiel.referencer(A, fiche("C", null));
        referentiel.referencer(A, fiche("A", TypeTraitementAnemie.EPO));
        referentiel.referencer(A, fiche("B", TypeTraitementAnemie.FER_INJECTABLE));
        assertEquals(List.of("A", "B", "C"), referentiel.lister(A).stream().map(x -> x.fiche().code()).toList());
        assertEquals(List.of("A"), referentiel.listerParTypeDeTraitement(A, TypeTraitementAnemie.EPO).stream()
                .map(x -> x.fiche().code()).toList());
    }

    @Test
    void la_recherche_est_paginee_et_bornee() {
        for (String code : List.of("A1", "A2", "A3")) {
            referentiel.referencer(A, fiche(code));
        }
        var page = referentiel.rechercher(A, " a ", null, 1, 2);
        assertEquals(3, page.total());
        assertEquals(List.of("A3"), page.articles().stream().map(x -> x.fiche().code()).toList());
        assertEquals(1, referentiel.rechercher(A, null, null, -1, 0).articles().size());
    }
}

