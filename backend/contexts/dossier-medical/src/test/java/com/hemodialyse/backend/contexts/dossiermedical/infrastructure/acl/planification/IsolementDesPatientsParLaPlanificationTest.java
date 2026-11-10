package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ReaffectationEnIsolement;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.VerrouDesPlacesDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La traduction vers le langage publié de la Planification : verrou pour un patient connu seulement, refus de la Planification inchangés. */
class IsolementDesPatientsParLaPlanificationTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));

    private final List<String> appels = new ArrayList<>();
    private boolean patientConnu = true;
    private RuntimeException refus;

    private final FichesDesPatients fiches = new FichesDesPatients() {
        @Override
        public boolean existe(UUID centre, UUID patient) {
            return patientConnu && centre.equals(CENTRE.valeur()) && patient.equals(PATIENT.valeur());
        }

        @Override
        public Map<UUID, NomDuPatient> noms(UUID centre, Collection<UUID> patients) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<IdentiteDuPatient> identite(UUID centre, UUID patient) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<CarteDuPatient> carte(UUID centre, UUID patient) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<UUID> parCode(UUID centre, String code) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<UUID> parNumeroDAssurance(UUID centre, String numero) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReferencesDuPatient> references(UUID centre) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Situation situation(UUID centre, UUID patient) {
            throw new UnsupportedOperationException();
        }
    };
    private final VerrouDesPlacesDuCentre verrou = centre -> appels.add("verrou " + centre);
    private final ReaffectationEnIsolement reaffectation = (centre, patient) -> {
        appels.add("siRisque " + centre + " " + patient);
        if (refus != null) throw refus;
        return ReaffectationEnIsolement.Issue.AUCUN_RISQUE;
    };
    private final IsolementDesPatientsParLaPlanification acl = new IsolementDesPatientsParLaPlanification(fiches, verrou, reaffectation);

    @Test
    void le_verrou_du_centre_est_pris_pour_un_patient_connu() {
        acl.verrouillerLesPlaces(CENTRE, PATIENT);
        assertThat(appels).containsExactly("verrou " + CENTRE.valeur());
    }

    @Test
    void aucun_verrou_n_est_pris_pour_un_patient_inconnu_du_centre() {
        patientConnu = false;
        acl.verrouillerLesPlaces(CENTRE, PATIENT);
        assertThat(appels).isEmpty();
    }

    @Test
    void la_reevaluation_passe_le_centre_et_le_patient_et_le_refus_de_la_planification_traverse_tel_quel() {
        acl.reevaluer(CENTRE, PATIENT);
        assertThat(appels).containsExactly("siRisque " + CENTRE.valeur() + " " + PATIENT.valeur());
        refus = new com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacementDesPatients.Refus("PLACEMENT_PATIENT_INTROUVABLE",
                "Patient introuvable dans ce centre");
        assertThatThrownBy(() -> acl.reevaluer(CENTRE, PATIENT)).isSameAs(refus);
    }
}
