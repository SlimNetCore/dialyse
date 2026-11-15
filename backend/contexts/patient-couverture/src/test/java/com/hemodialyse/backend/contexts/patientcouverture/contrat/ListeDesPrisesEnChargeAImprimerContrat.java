package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPrisesEnChargeAImprimer;
import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPrisesEnChargeAImprimer.PriseEnChargeImprimee;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat de la liste des prises en charge à imprimer (DOC5g, ADR 0199) : les mêmes tests passent sur le double en mémoire et sur H2. */
abstract class ListeDesPrisesEnChargeAImprimerContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("5f000000-0000-0000-0000-00000000002a"));
    protected static final CentreId AUTRE = new CentreId(UUID.fromString("5f000000-0000-0000-0000-00000000002b"));

    protected abstract ListeDesPrisesEnChargeAImprimer liste();

    /** Pose une prise en charge du centre ; le patient (code, nom, prénom, numéro d'assurance) est posé dans le même centre. */
    protected abstract void poser(CentreId centre, PriseEnChargeImprimee priseEnCharge);

    /** Pose une prise en charge du centre dont le patient appartient à {@code centreDuPatient}. */
    protected abstract void poserPourUnPatientDuCentre(CentreId centre, CentreId centreDuPatient, PriseEnChargeImprimee priseEnCharge);

    private static PriseEnChargeImprimee priseEnCharge(String code, String nom, int finJour) {
        return new PriseEnChargeImprimee(code, nom, "Sara", "ASS-" + code, LocalDate.of(2026, 1, 2), LocalDate.of(2027, 1, finJour),
                "VALIDEE");
    }

    @Test
    void la_liste_donne_les_valeurs_imprimees_de_chaque_prise_en_charge() {
        PriseEnChargeImprimee p = priseEnCharge("P-1", "Amrani", 5);
        poser(CENTRE, p);

        assertThat(liste().duCentre(CENTRE)).containsExactly(p);
    }

    @Test
    void la_liste_est_triee_par_date_de_fin_de_demande_decroissante() {
        poser(CENTRE, priseEnCharge("P-1", "Benali", 3));
        poser(CENTRE, priseEnCharge("P-2", "Amrani", 20));
        poser(CENTRE, priseEnCharge("P-3", "Zahi", 10));

        assertThat(liste().duCentre(CENTRE)).extracting(PriseEnChargeImprimee::codePatient).containsExactly("P-2", "P-3", "P-1");
    }

    @Test
    void les_dates_et_le_code_absents_restent_nuls() {
        poser(CENTRE, new PriseEnChargeImprimee(null, "Zahi", "Zoe", "ASS-9", null, null, "EN_ATTENTE"));

        PriseEnChargeImprimee lue = liste().duCentre(CENTRE).getFirst();

        assertThat(lue.codePatient()).isNull();
        assertThat(lue.dateDebutDemande()).isNull();
        assertThat(lue.dateFinDemande()).isNull();
        assertThat(lue.statut()).isEqualTo("EN_ATTENTE");
    }

    @Test
    void la_liste_est_bornee_au_centre() {
        poser(CENTRE, priseEnCharge("P-1", "Amrani", 5));
        poser(AUTRE, priseEnCharge("P-9", "Etranger", 6));

        assertThat(liste().duCentre(CENTRE)).extracting(PriseEnChargeImprimee::codePatient).containsExactly("P-1");
        assertThat(liste().duCentre(AUTRE)).extracting(PriseEnChargeImprimee::codePatient).containsExactly("P-9");
    }

    @Test
    void une_prise_en_charge_dont_le_patient_est_d_un_autre_centre_est_ecartee() {
        poserPourUnPatientDuCentre(CENTRE, AUTRE, priseEnCharge("P-9", "Etranger", 6));
        poser(CENTRE, priseEnCharge("P-1", "Amrani", 5));

        assertThat(liste().duCentre(CENTRE)).extracting(PriseEnChargeImprimee::codePatient).containsExactly("P-1");
    }

    @Test
    void un_centre_sans_prise_en_charge_donne_une_liste_vide() {
        poser(AUTRE, priseEnCharge("P-9", "Etranger", 6));

        assertThat(liste().duCentre(CENTRE)).isEmpty();
    }
}
