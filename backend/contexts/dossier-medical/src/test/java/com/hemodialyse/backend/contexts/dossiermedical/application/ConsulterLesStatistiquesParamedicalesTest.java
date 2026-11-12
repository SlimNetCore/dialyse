package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PeriodeInversee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.application.FixtureDesStatistiques.AUJOURDHUI;
import static com.hemodialyse.backend.contexts.dossiermedical.application.FixtureDesStatistiques.CENTRE;
import static com.hemodialyse.backend.contexts.dossiermedical.application.FixtureDesStatistiques.PATIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PL4 : les statistiques paramedicales d'un patient. Garde du centre de la session (403) AVANT la validation de la periode (400,
 * KB-PL4-5), puis le patient du centre (404 pour un inconnu ou d'un autre centre, KB-PL4-2), puis la lecture des seances.
 */
class ConsulterLesStatistiquesParamedicalesTest {

    private final FixtureDesStatistiques f = new FixtureDesStatistiques();
    private final ConsulterLesStatistiquesParamedicales consulter =
            new ConsulterLesStatistiquesParamedicales(f.session, f.patients, f.lectureDesSeances, f.medicales, f.horloge);

    @Test
    void should_rendre_les_mesures_des_seances_et_le_poids_sec_en_vigueur() {
        f.seances = new SeancesDuPatient.MesuresDesSeances(3, 70.5, 68.0, 1500.0, List.of(FixtureDesStatistiques.ligne("poids_avant_kg", 70.5)),
                List.of(FixtureDesStatistiques.tensions(LocalDate.of(2026, 1, 10), "130/80", "bizarre")));
        f.poidsSec = 66.5;

        StatistiquesParamedicales stats = consulter.executer(null, PATIENT, null, null);

        assertThat(stats.nombreDeSeances()).isEqualTo(3);
        assertThat(stats.moyennePoidsAvant()).isEqualTo(70.5);
        assertThat(stats.moyennePoidsApres()).isEqualTo(68.0);
        assertThat(stats.moyenneUltrafiltration()).isEqualTo(1500.0);
        assertThat(stats.poidsSecCible()).isEqualTo(66.5);
        assertThat(stats.evolutionDuPoids()).hasSize(1);
        assertThat(stats.evolutionDeLaTension()).singleElement().satisfies(t -> {
            assertThat(t.avant().systolique()).isEqualTo(130);
            assertThat(t.apres().estLisible()).isFalse();
        });
    }

    @Test
    void should_rendre_zero_pour_une_moyenne_sans_mesure_et_ne_pas_inventer_un_poids_sec() {
        StatistiquesParamedicales stats = consulter.executer(CENTRE.valeur(), PATIENT, null, null);

        assertThat(stats.moyennePoidsAvant()).isZero();
        assertThat(stats.moyennePoidsApres()).isZero();
        assertThat(stats.moyenneUltrafiltration()).isZero();
        assertThat(stats.poidsSecCible()).isNull();
        assertThat(stats.evolutionDuPoids()).isEmpty();
    }

    @Test
    void should_chercher_le_poids_sec_a_la_fin_de_la_periode_sinon_au_jour_du_centre() {
        consulter.executer(null, PATIENT, null, LocalDate.of(2026, 2, 1));
        assertThat(f.poidsSecDemandePour).isEqualTo(LocalDate.of(2026, 2, 1));

        consulter.executer(null, PATIENT, LocalDate.of(2026, 1, 1), null);
        assertThat(f.poidsSecDemandePour).isEqualTo(AUJOURDHUI);
    }

    @Test
    void should_transmettre_la_periode_aux_seances() {
        consulter.executer(null, PATIENT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(f.periodeLue.du()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(f.periodeLue.au()).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    @Test
    void should_refuser_un_autre_centre_avant_de_valider_la_periode_et_avant_toute_lecture() {
        UUID autreCentre = UUID.randomUUID();
        LocalDate fin = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> consulter.executer(autreCentre, PATIENT, LocalDate.of(2026, 2, 1), fin)).isInstanceOf(AccesRefuse.class);
        f.centreDeLaSession = null;
        assertThatThrownBy(() -> consulter.executer(null, PATIENT, null, null)).isInstanceOf(AccesRefuse.class);

        assertThat(f.appels).containsOnly("session");
    }

    @Test
    void should_refuser_une_periode_inversee_avant_de_lire_le_patient() {
        assertThatThrownBy(() -> consulter.executer(null, PATIENT, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(PeriodeInversee.class);

        assertThat(f.appels).containsExactly("session");
    }

    @Test
    void should_refuser_un_patient_inconnu_ou_d_un_autre_centre_avant_de_lire_les_seances() {
        assertThatThrownBy(() -> consulter.executer(null, UUID.randomUUID(), null, null)).isInstanceOf(PatientIntrouvable.class);

        assertThat(f.appels).containsExactly("session", "patient");
    }

    @Test
    void should_refuser_un_patient_absent_de_la_demande_avant_toute_lecture() {
        assertThatThrownBy(() -> consulter.executer(null, null, null, null)).isInstanceOf(SaisieInvalide.class);
        assertThat(f.appels).isEmpty();
    }
}
