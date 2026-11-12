package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.SessionFixe;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CompteursDeCouverture;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.AUJOURDHUI;
import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.AUTRE_CENTRE;
import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.CENTRE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PL3 : le tableau de bord compte au JOUR DU CENTRE (horloge injectée : scénarios déterministes), garde le centre de la session
 * (KB-PL3-1) avant toute lecture, refuse un délai négatif ou un mois illisible (KB-PL3-4) et ne compte plus les prises en charge déjà
 * échues parmi celles « à échéance » (KB-PL3-3).
 */
class ConsulterLeTableauDeBordAdministratifTest {

    /** Enregistre chaque lecture ; rend des comptes distincts pour repérer qui est lu où. */
    static final class CompteursEnregistres implements CompteursDeCouverture {
        final List<String> appels = new ArrayList<>();

        @Override
        public long prisesEnCharge(UUID centre, StatutDePec statut, LocalDate du, LocalDate au) {
            appels.add("pec " + statut + " " + du + ".." + au);
            return switch (statut) {
                case CREE -> 1;
                case VALIDEE -> 2;
                case CLOTUREE -> 3;
            };
        }

        @Override
        public long prisesEnChargeValideesAEcheance(UUID centre, LocalDate aujourdhui, LocalDate seuil) {
            appels.add("pecAEcheance " + aujourdhui + ".." + seuil);
            return 4;
        }

        @Override
        public long attestations(UUID centre, LocalDate du, LocalDate au) {
            appels.add("attestations " + du + ".." + au);
            return 5;
        }

        @Override
        public long attestationsAEcheance(UUID centre, LocalDate aujourdhui, LocalDate seuil) {
            appels.add("attestationsAEcheance " + aujourdhui + ".." + seuil);
            return 6;
        }

        @Override
        public long patientsCrees(UUID centre, LocalDate du, LocalDate au) {
            appels.add("patientsCrees " + du + ".." + au);
            return 7;
        }

        @Override
        public long patientsPresentsLe(UUID centre, LocalDate jour) {
            appels.add("patientsPresentsLe " + jour);
            return 8;
        }
    }

    private final CompteursEnregistres compteurs = new CompteursEnregistres();
    private final SessionFixe session = new SessionFixe();
    private final ConsulterLeTableauDeBordAdministratif tableau =
            new ConsulterLeTableauDeBordAdministratif(session, compteurs, new HorlogeFixe());

    @Test
    void sans_mois_le_tableau_compte_tout_et_l_effectif_du_jour_du_centre_avec_l_echeance_a_trente_jours() {
        TableauDeBordAdministratif t = tableau.executer(CENTRE.valeur(), null, null);

        assertThat(t).isEqualTo(new TableauDeBordAdministratif(1, 2, 4, 5, 6, 8, 30, null));
        assertThat(compteurs.appels).containsExactlyInAnyOrder(
                "pec CREE null..null", "pec VALIDEE null..null",
                "pecAEcheance " + AUJOURDHUI + ".." + AUJOURDHUI.plusDays(30),
                "attestations null..null", "attestationsAEcheance " + AUJOURDHUI + ".." + AUJOURDHUI.plusDays(30),
                "patientsPresentsLe " + AUJOURDHUI);
    }

    @Test
    void avec_un_mois_les_periodes_vont_du_premier_au_dernier_jour_et_les_patients_sont_ceux_crees_dans_le_mois() {
        TableauDeBordAdministratif t = tableau.executer(CENTRE.valeur(), 15, "2026-01");

        assertThat(t).isEqualTo(new TableauDeBordAdministratif(1, 2, 4, 5, 6, 7, 15, YearMonth.of(2026, 1)));
        assertThat(compteurs.appels).contains("pec CREE 2026-01-01..2026-01-31", "pec VALIDEE 2026-01-01..2026-01-31",
                "attestations 2026-01-01..2026-01-31", "patientsCrees 2026-01-01..2026-01-31",
                "pecAEcheance " + AUJOURDHUI + ".." + AUJOURDHUI.plusDays(15), "attestationsAEcheance " + AUJOURDHUI + ".." + AUJOURDHUI.plusDays(15));
        assertThat(compteurs.appels).noneMatch(a -> a.startsWith("patientsPresentsLe"));
    }

    @Test
    void un_delai_de_zero_jour_a_pour_seuil_le_jour_du_centre() {
        tableau.executer(CENTRE.valeur(), 0, null);

        assertThat(compteurs.appels).contains("pecAEcheance " + AUJOURDHUI + ".." + AUJOURDHUI);
    }

    @Test
    void le_centre_demande_doit_etre_celui_de_la_session_et_rien_n_est_lu_sinon() {
        assertThatThrownBy(() -> tableau.executer(AUTRE_CENTRE.valeur(), null, null)).isInstanceOf(AccesRefuse.class);

        session.centreDeLaSession = null;
        assertThatThrownBy(() -> tableau.executer(CENTRE.valeur(), null, null)).isInstanceOf(AccesRefuse.class);
        assertThat(compteurs.appels).isEmpty();
    }

    @Test
    void la_garde_de_centre_passe_avant_la_validation_des_parametres() {
        assertThatThrownBy(() -> tableau.executer(AUTRE_CENTRE.valeur(), -5, "n-importe-quoi")).isInstanceOf(AccesRefuse.class);
    }

    @Test
    void un_delai_negatif_ou_un_mois_illisible_sont_refuses_sans_lecture() {
        assertThatThrownBy(() -> tableau.executer(CENTRE.valeur(), -5, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> tableau.executer(CENTRE.valeur(), 30, "2026-13")).isInstanceOf(SaisieInvalide.class);
        assertThat(compteurs.appels).isEmpty();
    }
}
