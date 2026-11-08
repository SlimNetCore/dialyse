package com.hemodialyse.backend.contexts.seance.infrastructure.acl;

import com.hemodialyse.backend.contexts.seance.application.ConsommationRefusee;
import com.hemodialyse.backend.contexts.seance.domain.ArticleDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.ArticleId;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.QuantiteConsommee;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock.ArticlesDuCentreDuStock;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock.ConsommablesDeLaSeanceDuStock;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ArticleDuCatalogue;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE3 : traduction des langages publiés du Stock (déclaration des consommables, catalogue) et des refus, table-driven. */
class TraducteursDesConsommablesTest {

    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xA));
    private static final PatientId PATIENT = new PatientId(new UUID(1, 1));
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);
    private static final Seance SEANCE = Seance.creer(new SeanceId(new UUID(2, 1)), CENTRE, PATIENT, JOUR, Instant.parse("2026-06-15T08:00:00Z"));
    private static final ArticleId ARTICLE = new ArticleId(new UUID(6, 1));

    private final ConsommationsDeSeance stock = Mockito.mock(ConsommationsDeSeance.class);
    private final ConsommablesDeLaSeanceDuStock acl = new ConsommablesDeLaSeanceDuStock(stock);

    @Test
    void should_add_with_the_identities_the_day_of_the_session_the_quantity_as_typed_and_the_author() {
        acl.ajouter(CENTRE, SEANCE, ARTICLE, QuantiteConsommee.aAjouter(new BigDecimal("1.5")), "u-infirmier");

        Mockito.verify(stock).ajouter(CENTRE.valeur(), SEANCE.id().valeur(), PATIENT.valeur(), JOUR, ARTICLE.valeur(),
                new BigDecimal("1.5"), "u-infirmier");
    }

    @Test
    void should_replace_by_fixing_the_total_and_remove_by_fixing_zero() {
        acl.remplacer(CENTRE, SEANCE, ARTICLE, QuantiteConsommee.nouvelle(new BigDecimal("3")), "inf-01");
        acl.retirer(CENTRE, SEANCE, ARTICLE, "system");

        Mockito.verify(stock).fixer(CENTRE.valeur(), SEANCE.id().valeur(), PATIENT.valeur(), JOUR, ARTICLE.valeur(), new BigDecimal("3"),
                "inf-01");
        Mockito.verify(stock).fixer(CENTRE.valeur(), SEANCE.id().valeur(), PATIENT.valeur(), JOUR, ARTICLE.valeur(), BigDecimal.ZERO, "system");
    }

    /** Code du Stock, message du Stock, type traduit (400 : saisie invalide ; 422 : refus avec le code du Stock). */
    private record Refus(String code, boolean saisieInvalide) {
    }

    @Test
    void should_translate_every_stock_refusal_by_its_cause_whatever_the_route() {
        for (Refus refus : new Refus[]{new Refus("ARTICLE_INTROUVABLE", true), new Refus("ARTICLE_INACTIF", true),
                new Refus("QUANTITE_INVALIDE", true), new Refus("SEANCE_FACTUREE_NON_MODIFIABLE", false),
                new Refus("PIECE_DEJA_RAPPROCHEE", false), new Refus("CODE_FUTUR_DU_STOCK", false)}) {
            var erreur = new com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationRefusee(refus.code(), "message " + refus.code());
            Mockito.doThrow(erreur).when(stock).ajouter(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                    Mockito.any(), Mockito.any());
            Mockito.doThrow(erreur).when(stock).fixer(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                    Mockito.any(), Mockito.any());
            Runnable[] routes = {
                    () -> acl.ajouter(CENTRE, SEANCE, ARTICLE, QuantiteConsommee.aAjouter(BigDecimal.ONE), "a"),
                    () -> acl.remplacer(CENTRE, SEANCE, ARTICLE, QuantiteConsommee.nouvelle(BigDecimal.ONE), "a"),
                    () -> acl.retirer(CENTRE, SEANCE, ARTICLE, "a")};
            for (Runnable route : routes) {
                if (refus.saisieInvalide()) {
                    assertThatThrownBy(route::run).as(refus.code()).isInstanceOf(SaisieInvalide.class).hasMessage("message " + refus.code());
                } else {
                    assertThatThrownBy(route::run).as(refus.code()).isInstanceOfSatisfying(ConsommationRefusee.class,
                            e -> assertThat(e.code()).isEqualTo(refus.code())).hasMessage("message " + refus.code());
                }
            }
        }
    }

    @Test
    void should_translate_the_catalogue_article_into_code_and_active_flag() {
        UUID id = new UUID(6, 1);
        var catalogue = Mockito.mock(CatalogueDArticles.class);
        Mockito.when(catalogue.article(CENTRE.valeur(), id)).thenReturn(Optional.of(new ArticleDuCatalogue(id, CENTRE.valeur(), "ART-1",
                "Aiguille", "unité", false, null, null, null, false, null, null)));
        var acl = new ArticlesDuCentreDuStock(catalogue);

        assertThat(acl.trouver(CENTRE, new ArticleId(id))).contains(new ArticleDuCentre(new ArticleId(id), "ART-1", false));
        assertThat(acl.trouver(CENTRE, new ArticleId(new UUID(6, 9)))).isEmpty();
    }
}
