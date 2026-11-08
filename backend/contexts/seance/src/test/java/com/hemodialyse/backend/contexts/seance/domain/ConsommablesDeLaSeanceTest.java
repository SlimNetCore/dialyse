package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SE3 : gardes de statut des consommables déclarés (ajout, modification, retrait) et quantité consommée. Matrice reproduite du legacy
 * (KB-SEA-24) : l'ajout exige validée ou signée, la modification et le retrait n'excluent que la facturée.
 */
class ConsommablesDeLaSeanceTest {

    private static Seance seance(StatutDeSeance statut) {
        return Seance.restituer(new SeanceId(new UUID(5, 1)), new CentreId(new UUID(0, 0xA)), new PatientId(new UUID(7, 1)),
                LocalDate.of(2026, 6, 15), statut, Instant.parse("2026-06-15T08:00:00Z"), null, null);
    }

    @ParameterizedTest
    @EnumSource(value = StatutDeSeance.class, names = {"VALIDEE", "SIGNEE"})
    void should_accept_a_consumable_on_a_validated_or_signed_session(StatutDeSeance statut) {
        assertThatCode(() -> seance(statut).exigerLAjoutDeConsommables()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = StatutDeSeance.class, names = {"CREE", "ABSENT"})
    void should_refuse_adding_a_consumable_before_the_validation(StatutDeSeance statut) {
        assertThatThrownBy(() -> seance(statut).exigerLAjoutDeConsommables())
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                    assertThat(e.motif()).isEqualTo(MotifDeRefus.CONSOMMABLES_AVANT_VALIDATION);
                    assertThat(e.motif().code()).isEqualTo("BUSINESS_RULE_VIOLATION");
                })
                .hasMessage("La seance doit etre validee avant d'ajouter un consommable");
    }

    @Test
    void should_refuse_every_consumable_write_on_a_billed_session_before_looking_at_the_other_rules() {
        for (Runnable garde : new Runnable[]{() -> seance(StatutDeSeance.FACTUREE).exigerLAjoutDeConsommables(),
                () -> seance(StatutDeSeance.FACTUREE).exigerLaModificationDesConsommables()}) {
            assertThatThrownBy(garde::run).isInstanceOfSatisfying(SeanceRefusee.class,
                    e -> assertThat(e.motif().code()).isEqualTo("SEANCE_FACTUREE_NON_MODIFIABLE"))
                    .hasMessage("La seance facturee ne peut plus etre modifiee");
        }
    }

    @ParameterizedTest
    @EnumSource(value = StatutDeSeance.class, names = {"CREE", "ABSENT", "VALIDEE", "SIGNEE"})
    void should_accept_changing_or_removing_a_consumable_whatever_the_status_but_billed_KB_SEA_24_reproduced(StatutDeSeance statut) {
        assertThatCode(() -> seance(statut).exigerLaModificationDesConsommables()).doesNotThrowAnyException();
    }

    @Test
    void should_keep_the_quantity_as_typed_and_refuse_zero_negative_and_absent() {
        assertThat(QuantiteConsommee.aAjouter(new BigDecimal("1.23456789")).valeur()).isEqualByComparingTo("1.23456789");
        assertThat(QuantiteConsommee.aAjouter(new BigDecimal("1E+12")).valeur()).isEqualByComparingTo("1000000000000");
        for (BigDecimal invalide : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-1")}) {
            assertThatThrownBy(() -> QuantiteConsommee.aAjouter(invalide)).isInstanceOf(SaisieInvalide.class)
                    .hasMessage("La quantite doit etre strictement positive");
            assertThatThrownBy(() -> QuantiteConsommee.nouvelle(invalide)).isInstanceOf(SaisieInvalide.class)
                    .hasMessage("La nouvelle quantite doit etre strictement positive");
        }
    }

    @Test
    void should_refuse_an_absent_article_identifier() {
        assertThatThrownBy(() -> new ArticleId(null)).isInstanceOf(SaisieInvalide.class).hasMessage("Article requis");
    }
}
