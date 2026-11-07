package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.CauseDeNonFacturation;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import com.hemodialyse.backend.contexts.seance.domain.SituationDuPlanning;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static com.hemodialyse.backend.contexts.seance.application.Banc.JOUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreerUneSeanceTest {

    private final Banc banc = new Banc();

    @Test
    void should_create_a_session_in_the_status_cree_for_a_billable_patient_of_the_centre() {
        var patient = banc.patient(CENTRE_A, 1, "P-001", "A1");

        var enregistree = banc.creer().executer(CENTRE_A.valeur(), patient.valeur(), JOUR.plusDays(3));

        assertThat(enregistree.seance().statut()).isEqualTo(StatutDeSeance.CREE);
        assertThat(enregistree.seance().jour()).isEqualTo(JOUR.plusDays(3));
        assertThat(enregistree.seance().horsPlanning()).isFalse();
        assertThat(banc.seances.toutes()).containsExactly(enregistree.seance());
    }

    @Test
    void should_broadcast_the_creation_and_the_nurse_entry_after_the_commit() {
        var patient = banc.patient(CENTRE_A, 1, "P-001", "A1");

        banc.creer().executer(null, patient.valeur(), JOUR);

        assertThat(banc.diffusionsRecues).containsExactly("SEANCE_CREATED:Nom1", "SEANCE_CREEE:u-infirmier");
    }

    @Test
    void should_not_check_the_planning_when_posting_a_session() {
        var patient = banc.patient(CENTRE_A, 1, "P-001", "A1");
        banc.situations.put(Banc.cle(CENTRE_A, patient), new SituationDuPlanning(true, false, true, "DECEDE", JOUR, null));

        var enregistree = banc.creer().executer(null, patient.valeur(), JOUR);

        assertThat(enregistree.seance().horsPlanning()).isFalse();
        assertThat(enregistree.seance().statut()).isEqualTo(StatutDeSeance.CREE);
    }

    @Test
    void should_allow_several_sessions_for_the_same_patient_and_day() {
        var patient = banc.patient(CENTRE_A, 1, "P-001", "A1");

        banc.creer().executer(null, patient.valeur(), JOUR);
        banc.creer().executer(null, patient.valeur(), JOUR);

        assertThat(banc.seances.toutes()).hasSize(2);
    }

    @Test
    void should_take_the_day_of_the_centre_when_no_date_is_given() {
        var patient = banc.patient(CENTRE_A, 1, "P-001", "A1");
        banc.horlogeA("2026-06-14T23:30:00Z"); // minuit passé à Alger (UTC+1)

        var enregistree = banc.creer().executer(null, patient.valeur(), null);

        assertThat(enregistree.seance().jour()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void should_refuse_an_unknown_patient_and_write_nothing() {
        assertThatThrownBy(() -> banc.creer().executer(null, UUID.randomUUID(), JOUR)).isInstanceOf(PatientIntrouvable.class);
        assertThat(banc.seances.toutes()).isEmpty();
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    @Test
    void should_refuse_a_patient_of_another_centre_as_unknown() {
        var autre = banc.patient(CENTRE_B, 2, "P-002", "B2");

        assertThatThrownBy(() -> banc.creer().executer(CENTRE_A.valeur(), autre.valeur(), JOUR)).isInstanceOf(PatientIntrouvable.class);
        assertThat(banc.seances.toutes()).isEmpty();
    }

    @Test
    void should_refuse_a_centre_that_is_not_the_one_of_the_session_before_reading_the_patient() {
        assertThatThrownBy(() -> banc.creer().executer(CENTRE_B.valeur(), null, JOUR)).isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> banc.creer().executer(CENTRE_A.valeur(), null, JOUR)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_refuse_a_patient_who_cannot_be_billed_with_the_cause_of_the_refusal() {
        var patient = banc.patient(CENTRE_A, 1, "P-001", "A1");
        banc.causes.put(Banc.cle(CENTRE_A, patient), CauseDeNonFacturation.ATTESTATION_DE_DROITS);

        assertThatThrownBy(() -> banc.creer().executer(null, patient.valeur(), JOUR))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> assertThat(e.getMessage())
                        .startsWith("La prise en charge du patient est valide mais aucune attestation de droits ne couvre le 2026-06-15"));
        assertThat(banc.seances.toutes()).isEmpty();
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    @Test
    void should_specify_the_generator_of_the_patient_from_the_equipment() {
        var patient = banc.patient(CENTRE_A, 1, "P-001", "A1");
        UUID equipement = UUID.randomUUID();
        var fiche = banc.patients.fiches.get(Banc.cle(CENTRE_A, patient));
        banc.patients.fiches.put(Banc.cle(CENTRE_A, patient), new FicheDuPatient(fiche.id(), fiche.code(), fiche.nom(), fiche.prenom(),
                fiche.sexe(), fiche.dateDeNaissance(), fiche.numeroDAssurance(), null, null, null, null, null, equipement, "ancien", null, null));
        banc.equipements.put(equipement, new GenerateursDesPatients.Generateur(equipement, "GEN-7", "Fresenius", "EN_SERVICE"));

        var enregistree = banc.creer().executer(null, patient.valeur(), JOUR);

        assertThat(enregistree.patient().generateurNom()).isEqualTo("GEN-7");
        assertThat(enregistree.patient().generateurMarque()).isEqualTo("Fresenius");
        assertThat(enregistree.patient().generateurEtat()).isEqualTo("EN_SERVICE");
    }
}
