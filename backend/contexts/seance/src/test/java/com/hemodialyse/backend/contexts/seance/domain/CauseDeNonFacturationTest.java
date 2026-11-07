package com.hemodialyse.backend.contexts.seance.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CauseDeNonFacturationTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 3, 9);

    @Test
    void should_accept_a_billable_patient() {
        assertThatCode(() -> CauseDeNonFacturation.exigerFacturable(Optional.empty(), JOUR)).doesNotThrowAnyException();
    }

    @Test
    void should_refuse_each_cause_with_the_message_of_the_api() {
        verifier(CauseDeNonFacturation.PRISE_EN_CHARGE,
                "Le patient doit avoir une prise en charge valide pour être facturé");
        verifier(CauseDeNonFacturation.PRISE_EN_CHARGE_NON_VALIDEE,
                "La prise en charge du patient couvre le 2026-03-09 mais n'est pas validée : validez-la pour pouvoir le facturer");
        verifier(CauseDeNonFacturation.ATTESTATION_DE_DROITS,
                "La prise en charge du patient est valide mais aucune attestation de droits ne couvre le 2026-03-09 : "
                        + "renouvelez l'attestation pour pouvoir le facturer");
    }

    private static void verifier(CauseDeNonFacturation cause, String message) {
        assertThatThrownBy(() -> CauseDeNonFacturation.exigerFacturable(Optional.of(cause), JOUR))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                    assertThat(e.motif().code()).isEqualTo("BUSINESS_RULE_VIOLATION");
                    assertThat(e).hasMessage(message);
                });
    }
}
