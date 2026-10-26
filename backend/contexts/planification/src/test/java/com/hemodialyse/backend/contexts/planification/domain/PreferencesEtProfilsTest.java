package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreferencesEtProfilsTest {

    private static final PatientId PATIENT = new PatientId(UUID.fromString("99997800-0000-0000-0000-0000000000a1"));
    private static final InfirmierId INFIRMIER = new InfirmierId(UUID.fromString("99997800-0000-0000-0000-0000000000b1"));
    private static final CreneauId MATIN = new CreneauId(UUID.fromString("99997800-0000-0000-0000-0000000000c1"));

    @Test
    void should_have_no_preference_by_default() {
        PreferenceDePlanification aucune = PreferenceDePlanification.aucune(PATIENT);

        assertThat(aucune.creneauPrefere()).isNull();
        assertThat(aucune.seancesParSemaine()).isNull();
        assertThat(aucune.joursAChoisir()).isFalse();
    }

    @Test
    void should_accept_1_to_7_sessions_a_week() {
        assertThat(new PreferenceDePlanification(PATIENT, MATIN, 1, false).seancesParSemaine()).isEqualTo(1);
        assertThat(new PreferenceDePlanification(PATIENT, null, 7, true).seancesParSemaine()).isEqualTo(7);
        for (int seances : List.of(0, 8, -1)) {
            assertThatThrownBy(() -> new PreferenceDePlanification(PATIENT, null, seances, false))
                    .isInstanceOf(SaisieInvalide.class)
                    .hasMessage("Le nombre de séances par semaine doit être compris entre 1 et 7");
        }
    }

    @Test
    void should_require_the_number_of_sessions_to_let_the_days_be_chosen() {
        assertThatThrownBy(() -> new PreferenceDePlanification(PATIENT, null, null, true)).isInstanceOf(SaisieInvalide.class)
                .hasMessage("Le nombre de séances par semaine est requis pour choisir les jours");
        assertThat(new PreferenceDePlanification(PATIENT, null, null, false).joursAChoisir()).isFalse();
    }

    @Test
    void should_require_a_patient() {
        assertThatThrownBy(() -> new PreferenceDePlanification(null, null, null, false)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_work_full_time_without_skill_by_default() {
        ProfilDePlanification parDefaut = ProfilDePlanification.parDefaut(INFIRMIER);

        assertThat(parDefaut.tauxActivite()).isEqualTo(100);
        assertThat(parDefaut.competences()).isEmpty();
    }

    @Test
    void should_accept_an_activity_rate_from_10_to_100_percent() {
        assertThat(new ProfilDePlanification(INFIRMIER, 10, null).tauxActivite()).isEqualTo(10);
        assertThat(new ProfilDePlanification(INFIRMIER, 100, null).tauxActivite()).isEqualTo(100);
        for (int taux : List.of(9, 101, 0, -1)) {
            assertThatThrownBy(() -> new ProfilDePlanification(INFIRMIER, taux, Set.of())).isInstanceOf(SaisieInvalide.class)
                    .hasMessage("Le taux d'activité doit être compris entre 10 et 100 %");
        }
    }

    @Test
    void should_keep_the_skills_in_the_order_of_the_enumeration() {
        ProfilDePlanification profil = new ProfilDePlanification(INFIRMIER, 80, EnumSet.of(Competence.CATHETER, Competence.PEDIATRIE));

        assertThat(profil.competences()).containsExactly(Competence.PEDIATRIE, Competence.CATHETER);
    }

    @Test
    void should_require_a_nurse() {
        assertThatThrownBy(() -> new ProfilDePlanification(null, 80, Set.of())).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_refuse_a_negative_page_and_cap_the_size_at_100() {
        assertThatThrownBy(() -> new Pagination(-1, 20)).isInstanceOf(PaginationInvalide.class);
        assertThatThrownBy(() -> new Pagination(0, -1)).isInstanceOf(PaginationInvalide.class);
        assertThat(new Pagination(0, 0).taille()).isZero();
        assertThat(new Pagination(0, 100).taille()).isEqualTo(100);
        assertThat(new Pagination(0, 101).taille()).isEqualTo(100);
        assertThat(new Pagination(3, 20).decalage()).isEqualTo(60);
    }

    @Test
    void should_refuse_a_missing_identifier() {
        assertThatThrownBy(() -> new PatientId(null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new Identifiants.CentreId(null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new Identifiants.SalleId(null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new CreneauId(null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new InfirmierId(null)).isInstanceOf(SaisieInvalide.class);
    }
}
