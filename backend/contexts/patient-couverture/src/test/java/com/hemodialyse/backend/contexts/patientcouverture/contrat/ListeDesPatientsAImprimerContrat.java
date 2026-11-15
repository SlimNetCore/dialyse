package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPatientsAImprimer;
import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPatientsAImprimer.PatientImprime;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat de la liste des patients à imprimer (DOC5f, ADR 0198) : les mêmes tests passent sur le double en mémoire et sur H2. */
abstract class ListeDesPatientsAImprimerContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("5f000000-0000-0000-0000-00000000000a"));
    protected static final CentreId AUTRE = new CentreId(UUID.fromString("5f000000-0000-0000-0000-00000000000b"));

    protected abstract ListeDesPatientsAImprimer liste();

    protected abstract void poser(CentreId centre, PatientImprime patient);

    private static PatientImprime patient(String code, String nom, String prenom) {
        return new PatientImprime(code, nom, prenom, "F", "A+", LocalDate.of(1980, 1, 2), LocalDate.of(2026, 3, 4), "ASS-" + code,
                "0555 00 00 00", "ACTIF");
    }

    @Test
    void la_liste_donne_les_valeurs_imprimees_de_chaque_patient() {
        PatientImprime p = patient("P-1", "Amrani", "Sara");
        poser(CENTRE, p);

        assertThat(liste().duCentre(CENTRE)).containsExactly(p);
    }

    @Test
    void la_liste_est_triee_par_nom_puis_par_prenom() {
        poser(CENTRE, patient("P-1", "Benali", "Karim"));
        poser(CENTRE, patient("P-2", "Amrani", "Sara"));
        poser(CENTRE, patient("P-3", "Amrani", "Ali"));

        assertThat(liste().duCentre(CENTRE)).extracting(PatientImprime::codePatient).containsExactly("P-3", "P-2", "P-1");
    }

    @Test
    void les_valeurs_absentes_restent_nulles() {
        poser(CENTRE, new PatientImprime(null, "Zahi", "Zoe", "M", null, null, LocalDate.of(2026, 3, 4), "ASS-9", null, null));

        PatientImprime lu = liste().duCentre(CENTRE).getFirst();

        assertThat(lu.codePatient()).isNull();
        assertThat(lu.groupeSanguin()).isNull();
        assertThat(lu.dateNaissance()).isNull();
        assertThat(lu.telMobile()).isNull();
        assertThat(lu.etatPatient()).isNull();
    }

    @Test
    void la_liste_est_bornee_au_centre() {
        poser(CENTRE, patient("P-1", "Amrani", "Sara"));
        poser(AUTRE, patient("P-9", "Etranger", "Autre"));

        assertThat(liste().duCentre(CENTRE)).extracting(PatientImprime::codePatient).containsExactly("P-1");
        assertThat(liste().duCentre(AUTRE)).extracting(PatientImprime::codePatient).containsExactly("P-9");
    }

    @Test
    void un_centre_sans_patient_donne_une_liste_vide() {
        poser(AUTRE, patient("P-9", "Etranger", "Autre"));

        assertThat(liste().duCentre(CENTRE)).isEmpty();
    }
}
