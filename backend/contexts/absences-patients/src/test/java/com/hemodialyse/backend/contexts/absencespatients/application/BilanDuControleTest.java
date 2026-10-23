package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.application.BilanDuControle.Echec;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BilanDuControleTest {

    private static final LocalDate LUNDI = LocalDate.of(2026, 10, 19);
    private static final Echec ECHEC = new Echec(new PatientId(new UUID(1, 1)), LUNDI, "détection", "incident");

    @Test
    void deux_bilans_s_additionnent_et_les_jours_ignores_ne_se_repetent_pas() {
        BilanDuControle reconciliation = new BilanDuControle(0, 3, List.of(ECHEC), List.of(LUNDI));
        BilanDuControle detection = new BilanDuControle(5, 0, List.of(), List.of(LUNDI, LUNDI.plusDays(1)));

        BilanDuControle total = reconciliation.avec(detection);

        assertThat(total.creees()).isEqualTo(5);
        assertThat(total.annulees()).isEqualTo(3);
        assertThat(total.echecs()).containsExactly(ECHEC);
        assertThat(total.joursIgnores()).containsExactly(LUNDI, LUNDI.plusDays(1));
    }
}
