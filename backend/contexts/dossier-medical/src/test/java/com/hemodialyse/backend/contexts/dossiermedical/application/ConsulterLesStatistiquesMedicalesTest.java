package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PeriodeInversee;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.StatistiquesMedicalesPubliees;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.application.FixtureDesStatistiques.PATIENT;
import static com.hemodialyse.backend.contexts.dossiermedical.application.FixtureDesStatistiques.ligne;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PL4 : les statistiques medicales (moyennes, tendance des bilans, tendance de l'EPO) : memes gardes que le volet paramedical. */
class ConsulterLesStatistiquesMedicalesTest {

    private final FixtureDesStatistiques f = new FixtureDesStatistiques();
    private final ConsulterLesStatistiquesMedicales consulter = new ConsulterLesStatistiquesMedicales(f.session, f.patients, f.medicales);

    @Test
    void should_rendre_les_moyennes_et_les_deux_tendances_du_langage_publie() {
        f.moyennes = new StatistiquesMedicalesPubliees.Moyennes(11.2, 1.3, 400.0);
        f.bilans = List.of(ligne("DATE_PRELEVEMENT", "2026-01-10", "HB_G_DL", 10));
        f.epo = List.of(ligne("DATE_PRESCRIPTION", "2026-01-05", "EPO_DOSE_UI", 4000));

        StatistiquesMedicales stats = consulter.executer(null, PATIENT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1));

        assertThat(stats.hemoglobine()).isEqualTo(11.2);
        assertThat(stats.ktV()).isEqualTo(1.3);
        assertThat(stats.ferritine()).isEqualTo(400.0);
        assertThat(stats.tendanceDesBilans()).isEqualTo(f.bilans);
        assertThat(stats.tendanceDesPrescriptionsDEpo()).isEqualTo(f.epo);
    }

    @Test
    void should_garder_le_centre_puis_la_periode_puis_le_patient_avant_toute_lecture() {
        assertThatThrownBy(() -> consulter.executer(UUID.randomUUID(), PATIENT, null, null)).isInstanceOf(AccesRefuse.class);
        assertThat(f.appels).containsExactly("session");

        f.appels.clear();
        assertThatThrownBy(() -> consulter.executer(null, PATIENT, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(PeriodeInversee.class);
        assertThat(f.appels).containsExactly("session");

        f.appels.clear();
        assertThatThrownBy(() -> consulter.executer(null, UUID.randomUUID(), null, null)).isInstanceOf(PatientIntrouvable.class);
        assertThat(f.appels).containsExactly("session", "patient");
    }
}
