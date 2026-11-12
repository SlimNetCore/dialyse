package com.hemodialyse.backend.contexts.dossiermedical.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PL4 : la periode des statistiques a des bornes facultatives et incluses ; une borne de debut apres la fin est refusee (INVALID_DATE_RANGE). */
class PeriodeDeStatistiquesTest {

    private static final LocalDate JANVIER = LocalDate.of(2026, 1, 10);
    private static final LocalDate FEVRIER = LocalDate.of(2026, 2, 10);

    @Test
    void should_accepter_une_periode_sans_borne_ou_a_une_seule_borne() {
        assertThat(new PeriodeDeStatistiques(null, null).du()).isNull();
        assertThat(new PeriodeDeStatistiques(JANVIER, null).au()).isNull();
        assertThat(new PeriodeDeStatistiques(null, FEVRIER).du()).isNull();
    }

    @Test
    void should_accepter_un_seul_jour() {
        PeriodeDeStatistiques unJour = new PeriodeDeStatistiques(JANVIER, JANVIER);

        assertThat(unJour.du()).isEqualTo(unJour.au());
    }

    @Test
    void should_refuser_un_debut_apres_la_fin() {
        assertThatThrownBy(() -> new PeriodeDeStatistiques(FEVRIER, JANVIER))
                .isInstanceOf(PeriodeInversee.class)
                .isInstanceOf(SaisieInvalide.class);
    }
}
