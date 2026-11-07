package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DossierMedicalDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DossiersMedicauxDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnDossierMedical;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrat du port {@link DossiersMedicauxDeBase} : le double en mémoire, H2 et PostgreSQL 16 passent le MÊME jeu de tests. */
abstract class DossiersMedicauxDeBaseContrat {
    static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d8a1"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d8b2"));
    static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d8001"));
    static final PatientId AUTRE_PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d8002"));
    static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");

    protected abstract DossiersMedicauxDeBase dossiers();

    static DossierMedicalDeBase dossier(CentreId centre, PatientId patient, String nephropathie) {
        return DossierMedicalDeBase.ouvrir(UUID.randomUUID(), patient, centre, nephropathie, LocalDate.of(2026, 1, 11), "NEGATIF", "POSITIF",
                "Observation", "Conclusion", T0);
    }

    @Test
    void un_dossier_ajoute_est_relu_avec_tous_ses_champs() {
        var d = dossier(CENTRE_A, PATIENT, "Nephropathie fictive");
        dossiers().ajouter(d);
        var relu = dossiers().duPatient(CENTRE_A, PATIENT).orElseThrow();
        assertThat(relu.id()).isEqualTo(d.id());
        assertThat(relu.nephropathieInitiale()).isEqualTo("Nephropathie fictive");
        assertThat(relu.dateMiseEnDialyse()).isEqualTo(LocalDate.of(2026, 1, 11));
        assertThat(relu.hepatiteBStatut()).isEqualTo("NEGATIF");
        assertThat(relu.hepatiteCStatut()).isEqualTo("POSITIF");
        assertThat(relu.observationGlobale()).isEqualTo("Observation");
        assertThat(relu.conclusionMedicale()).isEqualTo("Conclusion");
        assertThat(relu.creeLe()).isEqualTo(T0);
        assertThat(relu.modifieLe()).isEqualTo(T0);
    }

    @Test
    void le_dossier_est_cherche_par_patient_et_par_centre() {
        dossiers().ajouter(dossier(CENTRE_A, PATIENT, "x"));
        assertThat(dossiers().duPatient(CENTRE_B, PATIENT)).isEmpty();
        assertThat(dossiers().duPatient(CENTRE_A, AUTRE_PATIENT)).isEmpty();
        assertThat(dossiers().duPatientEnVerrouillant(CENTRE_B, PATIENT)).isEmpty();
        assertThat(dossiers().duPatientEnVerrouillant(CENTRE_A, PATIENT)).isPresent();
    }

    @Test
    void le_remplacement_ecrase_le_contenu_et_conserve_l_identifiant_et_la_creation() {
        var d = dossier(CENTRE_A, PATIENT, "Avant");
        dossiers().ajouter(d);
        var charge = dossiers().duPatientEnVerrouillant(CENTRE_A, PATIENT).orElseThrow();
        charge.remplacer("Apres", null, null, null, null, null, T0.plusSeconds(60));
        dossiers().enregistrer(charge);
        var relu = dossiers().duPatient(CENTRE_A, PATIENT).orElseThrow();
        assertThat(relu.id()).isEqualTo(d.id());
        assertThat(relu.nephropathieInitiale()).isEqualTo("Apres");
        assertThat(relu.dateMiseEnDialyse()).isNull();
        assertThat(relu.hepatiteBStatut()).isNull();
        assertThat(relu.creeLe()).isEqualTo(T0);
        assertThat(relu.modifieLe()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void un_second_dossier_pour_le_meme_patient_dans_un_autre_centre_est_refuse_comme_une_ecriture_concurrente() {
        dossiers().ajouter(dossier(CENTRE_A, PATIENT, "Premier"));
        assertThatThrownBy(() -> dossiers().ajouter(dossier(CENTRE_B, PATIENT, "Second"))).isInstanceOf(EcritureConcurrenteSurUnDossierMedical.class);
        assertThat(dossiers().duPatient(CENTRE_B, PATIENT)).isEmpty();
        assertThat(dossiers().duPatient(CENTRE_A, PATIENT).orElseThrow().nephropathieInitiale()).isEqualTo("Premier");
    }

    @Test
    void le_verrou_d_enregistrement_est_pose_sans_erreur_hors_concurrence() {
        dossiers().verrouillerLEnregistrement(PATIENT);
        assertThat(dossiers().duPatient(CENTRE_A, PATIENT)).isEmpty();
    }
}
