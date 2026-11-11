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
    void un_assure_est_retrouve_quel_que_soit_son_centre_et_un_numero_inconnu_est_absent() {
        assures.mettreAJour(new Assure("ZA-AUTRE", "DU-CENTRE-B", null, null, null, null, null, null, null, null,
                AUTRE_CENTRE.valeur()));

        assertThat(reprise.trouver("ZA-AUTRE")).contains(new AssureConnu("ZA-AUTRE", AUTRE_CENTRE.valeur()));
        assertThat(reprise.trouver("za-autre")).isEmpty();
        assertThat(reprise.trouver("INCONNU")).isEmpty();
    }
}
