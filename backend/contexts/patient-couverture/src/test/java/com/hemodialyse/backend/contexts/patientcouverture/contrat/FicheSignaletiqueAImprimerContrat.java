package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.FicheSignaletiqueAImprimer;
import com.hemodialyse.backend.contexts.patientcouverture.application.FicheSignaletiqueAImprimer.FicheImprimable;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat de la fiche signalétique à imprimer (DOC5i, ADR 0201) : les mêmes tests passent sur le double en mémoire et sur H2. */
abstract class FicheSignaletiqueAImprimerContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("5f000000-0000-0000-0000-00000000091a"));
    protected static final CentreId AUTRE = new CentreId(UUID.fromString("5f000000-0000-0000-0000-00000000091b"));
    protected static final UUID PATIENT = UUID.fromString("5f000000-0000-0000-0000-0000000009c1");

    protected abstract FicheSignaletiqueAImprimer fiches();

    /** Pose un patient du centre avec les valeurs imprimées. */
    protected abstract void poser(CentreId centre, UUID patient, FicheImprimable fiche);

    private static FicheImprimable complete() {
        return new FicheImprimable("P-001", "Mme", "Amrani", "Sara", "F", "O+", LocalDate.of(1980, 5, 6), "Béjaïa",
                LocalDate.of(2026, 3, 4), 3, "Mariée", "Institutrice", "12 rue de l'Été", "021 00 00 01", "0550 00 00 01", "021 00 00 02",
                "sara@exemple.test", "ASS-1", "ACTIF", "Conjoint", "Allergie à l'iode", true, "Amrani", "Karim");
    }

    @Test
    void la_fiche_du_patient_donne_les_valeurs_imprimees() {
        FicheImprimable fiche = complete();
        poser(CENTRE, PATIENT, fiche);

        assertThat(fiches().duPatient(CENTRE, PATIENT)).contains(fiche);
    }

    @Test
    void les_valeurs_absentes_restent_nulles() {
        FicheImprimable fiche = new FicheImprimable(null, null, "Zahi", "Zoe", "M", null, null, null, LocalDate.of(2026, 3, 4), null, null,
                null, null, null, null, null, null, "", null, null, null, null, null, null);
        poser(CENTRE, PATIENT, fiche);

        assertThat(fiches().duPatient(CENTRE, PATIENT)).contains(fiche);
    }

    @Test
    void un_patient_d_un_autre_centre_est_introuvable() {
        poser(AUTRE, PATIENT, complete());

        assertThat(fiches().duPatient(CENTRE, PATIENT)).isEmpty();
        assertThat(fiches().duPatient(AUTRE, PATIENT)).isPresent();
    }

    @Test
    void un_patient_inconnu_ne_donne_rien() {
        assertThat(fiches().duPatient(CENTRE, UUID.randomUUID())).isEmpty();
    }
}
