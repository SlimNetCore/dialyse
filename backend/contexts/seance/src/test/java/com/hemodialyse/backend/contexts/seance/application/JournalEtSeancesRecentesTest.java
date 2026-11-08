package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static com.hemodialyse.backend.contexts.seance.application.Banc.JOUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE7a : journal du jour et séances récentes d'un patient, ports en mémoire (ADR 0094, KB-SEA-60 et 61). */
class JournalEtSeancesRecentesTest {

    private Banc banc;
    private PatientId patient;
    private final List<String> appels = new ArrayList<>();

    @BeforeEach
    void preparer() {
        banc = new Banc();
        patient = banc.patient(CENTRE_A, 1, "P001", "A1");
    }

    private ConsulterLeJournalDuJour journal() {
        PatientsDuJour patients = (centre, jour) -> {
            appels.add("patients:" + centre.valeur() + ":" + jour);
            return List.of(new PatientsDuJour.PatientDuJour(new SeanceId(new UUID(5, 1)), patient, jour, "CREE", "P001", "Nom", "Prenom"));
        };
        SortiesDuJour sorties = (centre, jour) -> {
            appels.add("sorties:" + centre.valeur() + ":" + jour);
            return List.of(new SortiesDuJour.SortieDArticle(new UUID(6, 1), "ART", "Article", new BigDecimal("3")));
        };
        return new ConsulterLeJournalDuJour(banc.session, patients, sorties);
    }

    @Test
    void should_give_the_patients_of_the_day_and_the_stock_outputs_of_the_same_day_for_the_centre_of_the_session() {
        var resultat = journal().executer(CENTRE_A.valeur(), JOUR);

        assertThat(resultat.jour()).isEqualTo(JOUR);
        assertThat(resultat.patients()).extracting(PatientsDuJour.PatientDuJour::statut).containsExactly("CREE");
        assertThat(resultat.sorties()).extracting(SortiesDuJour.SortieDArticle::code).containsExactly("ART");
        assertThat(appels).containsExactly("patients:" + CENTRE_A.valeur() + ":" + JOUR, "sorties:" + CENTRE_A.valeur() + ":" + JOUR);
    }

    @Test
    void should_refuse_another_centre_before_reading_anything_and_refuse_a_missing_day() {
        assertThatThrownBy(() -> journal().executer(CENTRE_B.valeur(), JOUR)).isInstanceOf(AccesRefuse.class);
        assertThat(appels).isEmpty();
        assertThatThrownBy(() -> journal().executer(CENTRE_A.valeur(), null)).isInstanceOf(SaisieInvalide.class);
    }

    // ---- séances récentes

    private ConsulterLesSeancesRecentes recentes() {
        VoletsDeLaSeance volets = new VoletsDeLaSeance() {
            @Override
            public Optional<VoletParamedical> paramedical(CentreId centre, SeanceId seance) {
                return seance.equals(new SeanceId(new UUID(5, 1)))
                        ? Optional.of(new VoletParamedical(new BigDecimal("70.1"), new BigDecimal("68.1"), "131/80", "121/70", 201, null,
                        new BigDecimal("1001"), null, null, null))
                        : Optional.empty();
            }

            @Override
            public Optional<VoletMedical> medical(CentreId centre, SeanceId seance) {
                throw new AssertionError("Les séances récentes n'exposent jamais le volet médical");
            }
        };
        return new ConsulterLesSeancesRecentes(banc.session, banc.seances, volets, banc.horloge());
    }

    private void semer(int n, int joursAvant, StatutDeSeance statut) {
        banc.seances.semer(Seance.restituer(new SeanceId(new UUID(5, n)), CENTRE_A, patient, JOUR.minusDays(joursAvant), statut,
                Instant.parse("2026-06-01T08:00:00Z"), null, null));
    }

    @Test
    void should_give_the_latest_sessions_before_today_newest_first_with_their_constants_whatever_their_status() {
        semer(1, 3, StatutDeSeance.CREE);
        semer(2, 4, StatutDeSeance.SIGNEE);
        semer(3, 0, StatutDeSeance.VALIDEE);

        var rendues = recentes().executer(CENTRE_A.valeur(), patient.valeur(), null, 3);

        assertThat(rendues).extracting(r -> r.seance().id().valeur()).containsExactly(new UUID(5, 1), new UUID(5, 2));
        assertThat(rendues.get(0).seance().statut()).isEqualTo(StatutDeSeance.CREE);
        assertThat(rendues.get(0).constantes().orElseThrow().poidsAvantKg()).isEqualByComparingTo("70.1");
        assertThat(rendues.get(1).constantes()).isEmpty();
    }

    @Test
    void should_take_the_day_of_the_centre_not_of_the_jvm_when_no_day_is_given_and_exclude_the_given_day() {
        semer(1, 1, StatutDeSeance.SIGNEE);
        semer(2, 0, StatutDeSeance.SIGNEE);

        assertThat(recentes().executer(CENTRE_A.valeur(), patient.valeur(), null, 3)).hasSize(1);
        assertThat(recentes().executer(CENTRE_A.valeur(), patient.valeur(), JOUR.plusDays(1), 3)).hasSize(2);
        assertThat(recentes().executer(CENTRE_A.valeur(), patient.valeur(), JOUR.minusDays(1), 3)).isEmpty();
    }

    @Test
    void should_bring_the_limit_back_to_one_when_zero_or_negative_and_to_ten_at_most_without_any_error() {
        for (int n = 1; n <= 12; n++) semer(n, n, StatutDeSeance.SIGNEE);

        assertThat(recentes().executer(CENTRE_A.valeur(), patient.valeur(), null, 0)).hasSize(1);
        assertThat(recentes().executer(CENTRE_A.valeur(), patient.valeur(), null, -4)).hasSize(1);
        assertThat(recentes().executer(CENTRE_A.valeur(), patient.valeur(), null, 50)).hasSize(10);
    }

    @Test
    void should_give_an_empty_list_for_an_unknown_patient_and_refuse_another_centre() {
        semer(1, 1, StatutDeSeance.SIGNEE);

        assertThat(recentes().executer(CENTRE_A.valeur(), new UUID(1, 99), null, 3)).isEmpty();
        assertThatThrownBy(() -> recentes().executer(CENTRE_B.valeur(), patient.valeur(), null, 3)).isInstanceOf(AccesRefuse.class);
    }
}
