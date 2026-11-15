package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesAttestationsAImprimer;
import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesAttestationsAImprimer.AttestationImprimee;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat de la liste des ouvertures de droit à imprimer (DOC5g, ADR 0199) : les mêmes tests passent sur le double en mémoire et sur H2. */
abstract class ListeDesAttestationsAImprimerContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("5f000000-0000-0000-0000-00000000001a"));
    protected static final CentreId AUTRE = new CentreId(UUID.fromString("5f000000-0000-0000-0000-00000000001b"));

    protected abstract ListeDesAttestationsAImprimer liste();

    /** Pose une ouverture de droit du centre ; le patient (code, nom, prénom, numéro d'assurance) est posé dans le même centre. */
    protected abstract void poser(CentreId centre, AttestationImprimee attestation);

    /** Pose une ouverture de droit du centre dont le patient appartient à {@code centreDuPatient}. */
    protected abstract void poserPourUnPatientDuCentre(CentreId centre, CentreId centreDuPatient, AttestationImprimee attestation);

    private static AttestationImprimee attestation(String code, String nom, int finJour) {
        return new AttestationImprimee(code, nom, "Sara", "ASS-" + code, LocalDate.of(2026, 1, 2), LocalDate.of(2027, 1, finJour));
    }

    @Test
    void la_liste_donne_les_valeurs_imprimees_de_chaque_ouverture_de_droit() {
        AttestationImprimee a = attestation("P-1", "Amrani", 5);
        poser(CENTRE, a);

        assertThat(liste().duCentre(CENTRE)).containsExactly(a);
    }

    @Test
    void la_liste_est_triee_par_date_de_fin_decroissante() {
        poser(CENTRE, attestation("P-1", "Benali", 3));
        poser(CENTRE, attestation("P-2", "Amrani", 20));
        poser(CENTRE, attestation("P-3", "Zahi", 10));

        assertThat(liste().duCentre(CENTRE)).extracting(AttestationImprimee::codePatient).containsExactly("P-2", "P-3", "P-1");
    }

    @Test
    void le_code_absent_du_patient_reste_nul() {
        poser(CENTRE, new AttestationImprimee(null, "Zahi", "Zoe", "ASS-9", LocalDate.of(2026, 1, 2), LocalDate.of(2027, 1, 2)));

        assertThat(liste().duCentre(CENTRE).getFirst().codePatient()).isNull();
    }

    @Test
    void la_liste_est_bornee_au_centre() {
        poser(CENTRE, attestation("P-1", "Amrani", 5));
        poser(AUTRE, attestation("P-9", "Etranger", 6));

        assertThat(liste().duCentre(CENTRE)).extracting(AttestationImprimee::codePatient).containsExactly("P-1");
        assertThat(liste().duCentre(AUTRE)).extracting(AttestationImprimee::codePatient).containsExactly("P-9");
    }

    @Test
    void une_ouverture_de_droit_dont_le_patient_est_d_un_autre_centre_est_ecartee() {
        poserPourUnPatientDuCentre(CENTRE, AUTRE, attestation("P-9", "Etranger", 6));
        poser(CENTRE, attestation("P-1", "Amrani", 5));

        assertThat(liste().duCentre(CENTRE)).extracting(AttestationImprimee::codePatient).containsExactly("P-1");
    }

    @Test
    void un_centre_sans_ouverture_de_droit_donne_une_liste_vide() {
        poser(AUTRE, attestation("P-9", "Etranger", 6));

        assertThat(liste().duCentre(CENTRE)).isEmpty();
    }
}
