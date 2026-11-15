package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.AttestationAImprimer;
import com.hemodialyse.backend.contexts.patientcouverture.application.AttestationAImprimer.AttestationImprimable;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat de l'ouverture de droit à imprimer (DOC5h, ADR 0200) : les mêmes tests passent sur le double en mémoire et sur H2. */
abstract class AttestationAImprimerContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("5f000000-0000-0000-0000-00000000081a"));
    protected static final CentreId AUTRE = new CentreId(UUID.fromString("5f000000-0000-0000-0000-00000000081b"));
    protected static final UUID PATIENT = UUID.fromString("5f000000-0000-0000-0000-0000000008a1");
    protected static final UUID AUTRE_PATIENT = UUID.fromString("5f000000-0000-0000-0000-0000000008a2");
    protected static final UUID ATTESTATION = UUID.fromString("5f000000-0000-0000-0000-0000000008b1");
    protected static final UUID ATTESTATION_RECENTE = UUID.fromString("5f000000-0000-0000-0000-0000000008b2");

    protected abstract AttestationAImprimer attestations();

    /** Pose une ouverture de droit du centre ; son patient (identifié) est posé, avec l'état civil imprimé, dans {@code centreDuPatient}. */
    protected abstract void poser(CentreId centre, CentreId centreDuPatient, UUID patient, UUID attestation, AttestationImprimable a);

    private static AttestationImprimable attestation(String nom, int finJour) {
        return new AttestationImprimable(nom, "Sara", "F", LocalDate.of(1980, 5, 6), "ASS-" + nom, "12 rue de l'Étoile", "0550 12 34 56",
                LocalDate.of(2026, 1, 2), LocalDate.of(2027, 1, finJour));
    }

    @Test
    void l_attestation_donne_les_valeurs_imprimees_par_son_identifiant() {
        AttestationImprimable a = attestation("Amrani", 5);
        poser(CENTRE, CENTRE, PATIENT, ATTESTATION, a);

        assertThat(attestations().parIdentifiant(CENTRE, ATTESTATION)).contains(a);
    }

    @Test
    void les_valeurs_absentes_restent_nulles() {
        AttestationImprimable a = new AttestationImprimable("Zahi", "Zoe", "M", null, "", null, null, LocalDate.of(2026, 1, 2),
                LocalDate.of(2027, 1, 2));
        poser(CENTRE, CENTRE, PATIENT, ATTESTATION, a);

        assertThat(attestations().parIdentifiant(CENTRE, ATTESTATION)).contains(a);
    }

    @Test
    void la_plus_recente_du_patient_est_celle_dont_la_date_de_fin_est_la_plus_tardive() {
        poser(CENTRE, CENTRE, PATIENT, ATTESTATION, attestation("Amrani", 5));
        poser(CENTRE, CENTRE, PATIENT, ATTESTATION_RECENTE, attestation("Amrani", 25));
        poser(CENTRE, CENTRE, AUTRE_PATIENT, UUID.randomUUID(), attestation("Autre", 28));

        assertThat(attestations().laPlusRecenteDuPatient(CENTRE, PATIENT)).map(AttestationImprimable::dateFin)
                .contains(LocalDate.of(2027, 1, 25));
    }

    @Test
    void une_attestation_d_un_autre_centre_est_introuvable() {
        poser(AUTRE, AUTRE, PATIENT, ATTESTATION, attestation("Etranger", 6));

        assertThat(attestations().parIdentifiant(CENTRE, ATTESTATION)).isEmpty();
        assertThat(attestations().laPlusRecenteDuPatient(CENTRE, PATIENT)).isEmpty();
        assertThat(attestations().parIdentifiant(AUTRE, ATTESTATION)).isPresent();
    }

    @Test
    void une_attestation_dont_le_patient_est_d_un_autre_centre_est_ecartee() {
        poser(CENTRE, AUTRE, PATIENT, ATTESTATION, attestation("Etranger", 6));

        assertThat(attestations().parIdentifiant(CENTRE, ATTESTATION)).isEmpty();
        assertThat(attestations().laPlusRecenteDuPatient(CENTRE, PATIENT)).isEmpty();
    }

    @Test
    void un_identifiant_inconnu_ne_donne_rien() {
        assertThat(attestations().parIdentifiant(CENTRE, UUID.randomUUID())).isEmpty();
        assertThat(attestations().laPlusRecenteDuPatient(CENTRE, UUID.randomUUID())).isEmpty();
    }
}
