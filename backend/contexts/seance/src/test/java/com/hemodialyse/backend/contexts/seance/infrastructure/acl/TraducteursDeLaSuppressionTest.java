package com.hemodialyse.backend.contexts.seance.infrastructure.acl;

import com.hemodialyse.backend.contexts.seance.application.ConsommationRefusee;
import com.hemodialyse.backend.contexts.seance.domain.MotifDeSuppression;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.SuppressionDeSeance;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.DiffusionsParLeLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.SaisiesParLeLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock.ConsommationsAnnuleesParLeStock;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.DiffusionsFournies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SaisiesDeSeanceFournies;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE6 : traducteurs de la suppression (ordre des effacements, refus du Stock, diffusion) : modèle publié vers modèle de la Séance. */
class TraducteursDeLaSuppressionTest {

    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xA));
    private static final SeanceId SEANCE = new SeanceId(new UUID(2, 1));
    private static final PatientId PATIENT = new PatientId(new UUID(1, 1));

    /** SE7f (ADR 0100) : les administrations restent fournies par la plateforme, les deux volets sont effacés par les dépôts de la Séance. */
    @Test
    void should_erase_the_administrations_then_the_paramedical_then_the_medical_entry_of_the_session() {
        var fournies = Mockito.mock(SaisiesDeSeanceFournies.class);
        var paramedicaux = Mockito.mock(com.hemodialyse.backend.contexts.seance.domain.VoletsParamedicaux.class);
        var medicaux = Mockito.mock(com.hemodialyse.backend.contexts.seance.domain.VoletsMedicaux.class);

        new SaisiesParLeLegacy(fournies, paramedicaux, medicaux).effacer(CENTRE, SEANCE);

        InOrder ordre = Mockito.inOrder(fournies, paramedicaux, medicaux);
        ordre.verify(fournies).effacerLesAdministrations(CENTRE.valeur(), SEANCE.valeur());
        ordre.verify(paramedicaux).effacer(CENTRE, SEANCE);
        ordre.verify(medicaux).effacer(CENTRE, SEANCE);
        Mockito.verifyNoMoreInteractions(fournies, paramedicaux, medicaux);
    }

    @Test
    void should_ask_the_stock_to_cancel_the_consumptions_of_the_session_with_the_author() {
        var stock = Mockito.mock(ConsommationsDeSeance.class);

        new ConsommationsAnnuleesParLeStock(stock).annuler(CENTRE, SEANCE, "u-admin");

        Mockito.verify(stock).annuler(CENTRE.valeur(), SEANCE.valeur(), "u-admin");
        Mockito.verifyNoMoreInteractions(stock);
    }

    @Test
    void should_translate_every_refusal_of_the_stock_into_a_422_with_the_code_of_the_stock() {
        for (String code : new String[]{"SEANCE_FACTUREE_NON_MODIFIABLE", "PIECE_DEJA_RAPPROCHEE", "ARTICLE_INTROUVABLE"}) {
            var stock = Mockito.mock(ConsommationsDeSeance.class);
            Mockito.doThrow(new com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationRefusee(code, "refus " + code))
                    .when(stock).annuler(CENTRE.valeur(), SEANCE.valeur(), "u-admin");

            assertThatThrownBy(() -> new ConsommationsAnnuleesParLeStock(stock).annuler(CENTRE, SEANCE, "u-admin"))
                    .isInstanceOfSatisfying(ConsommationRefusee.class, e -> {
                        assertThat(e.code()).isEqualTo(code);
                        assertThat(e.getMessage()).isEqualTo("refus " + code);
                    });
        }
    }

    @Test
    void should_publish_the_removal_with_the_identifiers_and_the_iso_day_of_the_session() {
        var fournies = Mockito.mock(DiffusionsFournies.class);
        var suppression = new SuppressionDeSeance(new UUID(9, 1), CENTRE, SEANCE, PATIENT, LocalDate.of(2026, 6, 15), StatutDeSeance.SIGNEE,
                new MotifDeSuppression("Doublon"), "u-admin", Instant.parse("2026-06-15T10:00:00Z"));

        new DiffusionsParLeLegacy(fournies).seanceSupprimee(CENTRE, suppression);

        Mockito.verify(fournies).seanceSupprimee(CENTRE.valeur(), SEANCE.valeur(), PATIENT.valeur(), "2026-06-15");
    }
}
