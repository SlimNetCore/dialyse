package com.hemodialyse.backend.contexts.stock.interfaces.catalogue;

import com.hemodialyse.backend.contexts.stock.application.referentiel.ReferentielDesArticles;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.TypeTraitementAnemie;
import com.hemodialyse.backend.contexts.stock.doubles.ArticlesEnMemoire;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ArticleDuCatalogue;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles.RefusDuCatalogue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Langage publié {@link CatalogueDArticles} : ce que la Séance et le Dossier médical voient d'un article (ADR 0006).
 */
class CatalogueDArticlesDuReferentielTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    private final ReferentielDesArticles referentiel =
            new ReferentielDesArticles(new ArticlesEnMemoire(), UUID::randomUUID, Clock.systemUTC());
    private final CatalogueDArticles catalogue = new CatalogueDArticlesDuReferentiel(referentiel);

    private UUID epo(UUID centre) {
        return referentiel.referencer(new CentreId(centre), FicheArticle.saisie("EPO3000", "Epoetine 3000", null, null, null,
                null, "seringue", null, null, new BigDecimal("3000"), "UI", null, null, null, null, null, true, true, null,
                false, false, TypeTraitementAnemie.EPO, "31100", "60110")).id();
    }

    @Test
    void l_article_publie_porte_identite_unites_dosage_et_comptes() {
        UUID id = epo(A);
        ArticleDuCatalogue a = catalogue.article(A, id).orElseThrow();
        assertEquals(new ArticleDuCatalogue(id, A, "EPO3000", "Epoetine 3000", "seringue", true, "EPO",
                new BigDecimal("3000"), "UI", true, "31100", "60110"), a);
        assertTrue(catalogue.article(B, id).isEmpty());
        assertEquals(1, catalogue.articles(A).size());
        assertEquals(1, catalogue.articlesDeTraitement(A, "EPO").size());
        assertEquals(0, catalogue.articlesDeTraitement(A, "FER_INJECTABLE").size());
    }

    @Test
    void un_type_de_traitement_inconnu_est_refuse_avec_un_code_stable() {
        assertEquals("ARTICLE_TYPE_TRAITEMENT_INVALIDE",
                assertThrows(RefusDuCatalogue.class, () -> catalogue.articlesDeTraitement(A, "VITAMINE")).code());
    }

    @Test
    void la_conversion_de_dose_passe_par_le_dosage_de_l_article() {
        UUID id = epo(A);
        assertEquals(new BigDecimal("1.3333"), catalogue.quantitePourDose(A, id, new BigDecimal("4000"), "UI"));
        assertEquals("ARTICLE_UNITE_DOSE_INCOMPATIBLE",
                assertThrows(RefusDuCatalogue.class, () -> catalogue.quantitePourDose(A, id, BigDecimal.ONE, "mg")).code());
        assertEquals("ARTICLE_INTROUVABLE",
                assertThrows(RefusDuCatalogue.class, () -> catalogue.quantitePourDose(B, id, BigDecimal.ONE, "UI")).code());
    }
}

