package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.AssuresDuCentre.Assure;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.AssuresEnMemoire;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAssures.AssureConnu;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAssures.ReportDeLAssure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.AUTRE_CENTRE;
import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.CENTRE;
import static org.assertj.core.api.Assertions.assertThat;

/** Reprise des assurés : une reprise crée ou met à jour, n'efface jamais une donnée, et retrouve un assuré de tout centre. */
class ReprendreLesAssuresTest {

    private AssuresEnMemoire assures;
    private ReprendreLesAssures reprise;

    @BeforeEach
    void preparer() {
        assures = new AssuresEnMemoire();
        reprise = new ReprendreLesAssures(assures);
    }

    @Test
    void un_assure_inconnu_est_cree_avec_les_valeurs_du_fichier() {
        reprise.reprendre(CENTRE.valeur(), new ReportDeLAssure("ZA100", "BENALI", "Ahmed", "M", LocalDate.of(1962, 3, 15),
                "0550", "0661", null, "12 rue", "O+"));

        assertThat(assures.trouver("ZA100")).contains(new Assure("ZA100", "BENALI", "Ahmed", "M", LocalDate.of(1962, 3, 15),
                "0550", "0661", null, "12 rue", "O+", CENTRE.valeur()));
    }

    @Test
    void une_reprise_n_efface_jamais_une_donnee_renseignee_et_remplace_le_nom_et_les_valeurs_du_fichier() {
        assures.mettreAJour(new Assure("ZA600", "ANCIEN", "Prenom-conserve", "F", LocalDate.of(1970, 1, 1), "0111", null, null,
                "Adresse conservee", "A+", CENTRE.valeur()));

        reprise.reprendre(CENTRE.valeur(), new ReportDeLAssure("ZA600", "NOUVEAU", null, "M", null, null, "0772", null, null, null));

        assertThat(assures.trouver("ZA600")).contains(new Assure("ZA600", "NOUVEAU", "Prenom-conserve", "M",
                LocalDate.of(1970, 1, 1), "0111", "0772", null, "Adresse conservee", "A+", CENTRE.valeur()));
    }

    @Test
    void un_assure_est_retrouve_sans_tenir_compte_de_la_casse_sous_son_numero_canonique() {
        assures.mettreAJour(new Assure("ZA901", "KACI", null, null, null, null, null, null, null, null, CENTRE.valeur()));

        assertThat(reprise.trouverSansCasse("za901")).contains(new AssureConnu("ZA901", CENTRE.valeur()));
        assertThat(reprise.trouverSansCasse("ZA901")).contains(new AssureConnu("ZA901", CENTRE.valeur()));
        assertThat(reprise.trouverSansCasse("ZA-INCONNU")).isEmpty();
    }

    @Test
    void l_etat_d_un_assure_est_memorise_puis_restaure_a_l_identique_y_compris_les_valeurs_vides_et_les_caracteres_speciaux() {
        Assure origine = new Assure("ZR1", "EXISTANT & Fils", null, "M", LocalDate.of(1970, 1, 1), "05 50", null, null,
                "12 rue de l'été = 3", null, CENTRE.valeur());
        assures.mettreAJour(origine);
        String memento = reprise.memoriser("ZR1").orElseThrow();
        reprise.reprendre(CENTRE.valeur(), new ReportDeLAssure("ZR1", "MIS-A-JOUR", "Prenom", "F", LocalDate.of(1980, 2, 2), "0111",
                "0661", "0772", "Autre adresse", "O+"));

        reprise.restaurer(CENTRE.valeur(), "ZR1", memento);

        assertThat(assures.trouver("ZR1")).contains(origine);
    }

    @Test
    void l_etat_du_pire_cas_du_schema_est_memorise_puis_restaure_a_l_identique() {
        String large = "€".repeat(255);
        Assure origine = new Assure("ZR2", large, large, large, LocalDate.of(1970, 1, 1), large, large, large, large, large, CENTRE.valeur());
        assures.mettreAJour(origine);
        String memento = reprise.memoriser("ZR2").orElseThrow();
        reprise.reprendre(CENTRE.valeur(), new ReportDeLAssure("ZR2", "AUTRE", "P", "F", null, "1", "2", "3", "A", "O+"));

        reprise.restaurer(CENTRE.valeur(), "ZR2", memento);

        assertThat(memento).hasSize(18467);
        assertThat(assures.trouver("ZR2")).contains(origine);
    }

    @Test
    void un_assure_inconnu_n_a_pas_d_etat_a_memoriser_et_un_assure_d_un_autre_centre_n_est_jamais_restaure() {
        Assure autre = new Assure("ZA-AUTRE", "DU-CENTRE-B", null, null, null, null, null, null, null, null, AUTRE_CENTRE.valeur());
        assures.mettreAJour(autre);
        String memento = reprise.memoriser("ZA-AUTRE").orElseThrow();
        assures.mettreAJour(new Assure("ZA-AUTRE", "MODIFIE", null, null, null, null, null, null, null, null, AUTRE_CENTRE.valeur()));

        reprise.restaurer(CENTRE.valeur(), "ZA-AUTRE", memento);

        assertThat(reprise.memoriser("ZA-INCONNU")).isEmpty();
        assertThat(assures.trouver("ZA-AUTRE").orElseThrow().nom()).isEqualTo("MODIFIE");
    }

    @Test
    void un_assure_est_retrouve_quel_que_soit_son_centre_et_un_numero_inconnu_est_absent() {
        assures.mettreAJour(new Assure("ZA-AUTRE", "DU-CENTRE-B", null, null, null, null, null, null, null, null,
                AUTRE_CENTRE.valeur()));

        assertThat(reprise.trouver("ZA-AUTRE")).contains(new AssureConnu("ZA-AUTRE", AUTRE_CENTRE.valeur()));
        assertThat(reprise.trouver("za-autre")).isEmpty();
        assertThat(reprise.trouver("INCONNU")).isEmpty();
    }
}
