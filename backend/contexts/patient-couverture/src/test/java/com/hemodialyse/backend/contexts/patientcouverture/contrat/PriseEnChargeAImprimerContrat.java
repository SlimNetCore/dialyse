package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.PriseEnChargeAImprimer;
import com.hemodialyse.backend.contexts.patientcouverture.application.PriseEnChargeAImprimer.PriseEnChargeImprimable;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat de la prise en charge à imprimer (DOC5h, ADR 0200) : les mêmes tests passent sur le double en mémoire et sur H2. */
abstract class PriseEnChargeAImprimerContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("5f000000-0000-0000-0000-00000000082a"));
    protected static final CentreId AUTRE = new CentreId(UUID.fromString("5f000000-0000-0000-0000-00000000082b"));
    protected static final UUID PATIENT = UUID.fromString("5f000000-0000-0000-0000-0000000008c1");
    protected static final UUID AUTRE_PATIENT = UUID.fromString("5f000000-0000-0000-0000-0000000008c2");
    protected static final UUID PEC = UUID.fromString("5f000000-0000-0000-0000-0000000008d1");
    protected static final UUID PEC_RECENTE = UUID.fromString("5f000000-0000-0000-0000-0000000008d2");

    protected abstract PriseEnChargeAImprimer prisesEnCharge();

    /** Pose une prise en charge du centre ; son patient (identifié) est posé, avec l'état civil imprimé, dans {@code centreDuPatient}. */
    protected abstract void poser(CentreId centre, CentreId centreDuPatient, UUID patient, UUID pec, PriseEnChargeImprimable p);

    private static PriseEnChargeImprimable pec(String nom, int finJour) {
        return new PriseEnChargeImprimable(nom, "Sara", "ASS-" + nom, LocalDate.of(1980, 5, 6), LocalDate.of(2026, 1, 2),
                LocalDate.of(2027, 1, finJour), "VALIDEE", LocalDate.of(2026, 2, 3), LocalDate.of(2026, 12, 30));
    }

    @Test
    void la_prise_en_charge_donne_les_valeurs_imprimees_par_son_identifiant() {
        PriseEnChargeImprimable p = pec("Amrani", 5);
        poser(CENTRE, CENTRE, PATIENT, PEC, p);

        assertThat(prisesEnCharge().parIdentifiant(CENTRE, PEC)).contains(p);
    }

    @Test
    void les_valeurs_absentes_restent_nulles() {
        PriseEnChargeImprimable p = new PriseEnChargeImprimable("Zahi", "Zoe", "", null, null, null, "BROUILLON", null, null);
        poser(CENTRE, CENTRE, PATIENT, PEC, p);

        assertThat(prisesEnCharge().parIdentifiant(CENTRE, PEC)).contains(p);
    }

    @Test
    void la_plus_recente_du_patient_est_celle_dont_la_date_de_fin_de_demande_est_la_plus_tardive() {
        poser(CENTRE, CENTRE, PATIENT, PEC, pec("Amrani", 5));
        poser(CENTRE, CENTRE, PATIENT, PEC_RECENTE, pec("Amrani", 25));
        poser(CENTRE, CENTRE, AUTRE_PATIENT, UUID.randomUUID(), pec("Autre", 28));

        assertThat(prisesEnCharge().laPlusRecenteDuPatient(CENTRE, PATIENT)).map(PriseEnChargeImprimable::dateFinDemande)
                .contains(LocalDate.of(2027, 1, 25));
    }

    @Test
    void une_prise_en_charge_d_un_autre_centre_est_introuvable() {
        poser(AUTRE, AUTRE, PATIENT, PEC, pec("Etranger", 6));

        assertThat(prisesEnCharge().parIdentifiant(CENTRE, PEC)).isEmpty();
        assertThat(prisesEnCharge().laPlusRecenteDuPatient(CENTRE, PATIENT)).isEmpty();
        assertThat(prisesEnCharge().parIdentifiant(AUTRE, PEC)).isPresent();
    }

    @Test
    void une_prise_en_charge_dont_le_patient_est_d_un_autre_centre_est_ecartee() {
        poser(CENTRE, AUTRE, PATIENT, PEC, pec("Etranger", 6));

        assertThat(prisesEnCharge().parIdentifiant(CENTRE, PEC)).isEmpty();
        assertThat(prisesEnCharge().laPlusRecenteDuPatient(CENTRE, PATIENT)).isEmpty();
    }

    @Test
    void un_identifiant_inconnu_ne_donne_rien() {
        assertThat(prisesEnCharge().parIdentifiant(CENTRE, UUID.randomUUID())).isEmpty();
        assertThat(prisesEnCharge().laPlusRecenteDuPatient(CENTRE, UUID.randomUUID())).isEmpty();
    }
}
