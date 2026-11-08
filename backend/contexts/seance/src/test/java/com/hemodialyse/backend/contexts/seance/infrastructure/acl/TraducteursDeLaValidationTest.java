package com.hemodialyse.backend.contexts.seance.infrastructure.acl;

import com.hemodialyse.backend.contexts.seance.application.ConsommationRefusee;
import com.hemodialyse.backend.contexts.seance.application.ConsommationsALaValidation;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PlaceDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.PlaceParLePlanning;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock.ConsommationsALaValidationDuStock;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlaceFournie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ArticleDuCatalogue;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE2 : traduction des langages publiés de la place du patient et des consommables à la validation (ACL). */
class TraducteursDeLaValidationTest {

    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xA));
    private static final PatientId PATIENT = new PatientId(new UUID(1, 1));
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);
    private static final Seance SEANCE = Seance.creer(new SeanceId(new UUID(2, 1)), CENTRE, PATIENT, JOUR, Instant.parse("2026-06-15T08:00:00Z"));

    @Test
    void should_translate_the_published_place_with_or_without_generator() {
        UUID salle = new UUID(3, 1);
        UUID creneau = new UUID(4, 1);
        var avec = new PlaceParLePlanning((c, p, j) -> Optional.of(new PlaceFournie.Place(salle, creneau, new UUID(5, 1))));
        var sans = new PlaceParLePlanning((c, p, j) -> Optional.of(new PlaceFournie.Place(salle, creneau, null)));
        var aucune = new PlaceParLePlanning((c, p, j) -> Optional.empty());

        assertThat(avec.placeLe(CENTRE, PATIENT, JOUR)).contains(new PlaceDeSeance(salle, creneau, new UUID(5, 1)));
        assertThat(sans.placeLe(CENTRE, PATIENT, JOUR)).contains(new PlaceDeSeance(salle, creneau, null));
        assertThat(aucune.placeLe(CENTRE, PATIENT, JOUR)).isEmpty();
    }

    @Test
    void should_translate_the_catalogue_article_into_code_and_active_flag() {
        UUID id = new UUID(6, 1);
        var catalogue = Mockito.mock(CatalogueDArticles.class);
        Mockito.when(catalogue.article(CENTRE.valeur(), id)).thenReturn(Optional.of(new ArticleDuCatalogue(id, CENTRE.valeur(), "ART-1",
                "Aiguille", "unité", false, null, null, null, false, null, null)));
        var acl = new ConsommationsALaValidationDuStock(catalogue, Mockito.mock(ConsommationsDeSeance.class));

        assertThat(acl.article(CENTRE, id)).contains(new ConsommationsALaValidation.Article(id, "ART-1", false));
        assertThat(acl.article(CENTRE, new UUID(6, 9))).isEmpty();
    }

    @Test
    void should_hand_the_consumables_to_the_stock_with_the_day_and_the_author() {
        var stock = Mockito.mock(ConsommationsDeSeance.class);
        var acl = new ConsommationsALaValidationDuStock(Mockito.mock(CatalogueDArticles.class), stock);
        UUID article = new UUID(6, 1);

        acl.enregistrer(CENTRE, SEANCE, List.of(new ConsommationsALaValidation.Ligne(article, new BigDecimal("2"))), "inf-01");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ConsommationsDeSeance.Ligne>> lignes = ArgumentCaptor.forClass(List.class);
        Mockito.verify(stock).enregistrerALaValidation(Mockito.eq(CENTRE.valeur()), Mockito.eq(SEANCE.id().valeur()),
                Mockito.eq(PATIENT.valeur()), Mockito.eq(JOUR), lignes.capture(), Mockito.eq("inf-01"));
        assertThat(lignes.getValue()).containsExactly(new ConsommationsDeSeance.Ligne(article, new BigDecimal("2")));
    }

    @Test
    void should_translate_a_refusal_of_the_stock_keeping_its_stable_code() {
        var stock = Mockito.mock(ConsommationsDeSeance.class);
        Mockito.doThrow(new com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationRefusee("SEANCE_FACTUREE_NON_MODIFIABLE",
                "facturée")).when(stock).enregistrerALaValidation(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any());
        var acl = new ConsommationsALaValidationDuStock(Mockito.mock(CatalogueDArticles.class), stock);

        assertThatThrownBy(() -> acl.enregistrer(CENTRE, SEANCE, List.of(), "inf"))
                .isInstanceOfSatisfying(ConsommationRefusee.class, e -> {
                    assertThat(e.code()).isEqualTo("SEANCE_FACTUREE_NON_MODIFIABLE");
                    assertThat(e.getMessage()).isEqualTo("facturée");
                });
    }
}
