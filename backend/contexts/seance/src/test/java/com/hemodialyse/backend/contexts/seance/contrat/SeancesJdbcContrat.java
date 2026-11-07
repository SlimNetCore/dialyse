package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.DerogationHorsPlanning;
import com.hemodialyse.backend.contexts.seance.domain.MotifHorsPlanning;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.SeancesJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port sur la table réelle {@code seances} (H2 et PostgreSQL 16 : sous-classes). */
abstract class SeancesJdbcContrat extends SeancesContrat {

    protected abstract JdbcTemplate jdbc();

    protected void vider() {
        for (Object centre : new Object[]{CENTRE_A.valeur(), CENTRE_B.valeur()}) {
            jdbc().update("DELETE FROM seances WHERE center_id = ?", centre);
        }
    }

    @Override
    protected Banc nouveauBanc() {
        vider();
        JdbcTemplate jdbc = jdbc();
        SeancesJdbc seances = new SeancesJdbc(jdbc);
        return new Banc() {
            @Override
            public Seances seances() {
                return seances;
            }

            @Override
            public void semer(Seance s) {
                var jalons = s.jalons();
                var derogation = s.derogation();
                jdbc.update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut, created_at, validated_at, "
                                + "signed_infirmier_at, signed_medecin_at, regularisation_deverrouillee_at, hors_planning, motif_hors_planning, "
                                + "precision_hors_planning) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", s.id().valeur(), s.centre().valeur(),
                        s.patient().valeur(), java.sql.Date.valueOf(s.jour()), s.statut().name(), horodatage(s.creeLe()),
                        horodatage(jalons.valideeLe()), horodatage(jalons.signeeParLInfirmierLe()), horodatage(jalons.signeeParLeMedecinLe()),
                        horodatage(jalons.deverrouilleePourRegularisationLe()), s.horsPlanning(),
                        derogation.map(d -> d.motif().name()).orElse(null), derogation.map(DerogationHorsPlanning::precision).orElse(null));
            }

            @Override
            public void semerUneLigneIllisible(CentreId centre, SeanceId id) {
                jdbc.update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut) VALUES (?,?,?,?,?)", id.valeur(),
                        centre.valeur(), PATIENT.valeur(), java.sql.Date.valueOf(JOUR), "EN_COURS");
            }
        };
    }

    private static Timestamp horodatage(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    @Test
    void la_table_garde_ses_colonnes_et_aucune_colonne_n_est_ajoutee() {
        List<String> colonnes = jdbc().queryForList("SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'seances'", String.class);
        assertThat(colonnes).containsExactlyInAnyOrder(TableDesSeances.COLONNES);
    }

    @Test
    void la_creation_n_ecrit_que_les_colonnes_de_la_creation_et_laisse_les_autres_nulles_comme_le_legacy() {
        var banc = nouveauBanc();
        banc.seances().ajouter(Seance.creerHorsPlanning(s(1), CENTRE_A, PATIENT, JOUR, T0,
                new DerogationHorsPlanning(MotifHorsPlanning.URGENCE, "Urgence", false)));

        var ligne = jdbc().queryForMap("SELECT * FROM seances WHERE id = ?", s(1).valeur());

        assertThat(ligne.get("STATUT")).isEqualTo("CREE");
        assertThat(ligne.get("HORS_PLANNING")).isEqualTo(true);
        assertThat(ligne.get("MOTIF_HORS_PLANNING")).isEqualTo("URGENCE");
        for (String nulle : List.of("validated_at", "signed_infirmier_at", "signed_infirmier_by", "signed_medecin_at", "signed_medecin_by",
                "facture_id", "forfait_override_id", "forfait_override_prix", "regularisation_deverrouillee_at", "salle_id", "creneau_id",
                "generateur_id", "statut_avant_exclusion", "exclusion_absence_id")) {
            assertThat(ligne.get(nulle.toUpperCase())).as(nulle).isNull();
        }
    }

    @Test
    void une_ligne_hors_planning_sans_motif_est_illisible_et_non_une_erreur_technique() {
        jdbc().update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut, hors_planning) VALUES (?,?,?,?,?,?)",
                s(5).valeur(), CENTRE_A.valeur(), PATIENT.valeur(), java.sql.Date.valueOf(JOUR), "CREE", true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new SeancesJdbc(jdbc()).trouver(CENTRE_A, s(5)))
                .isInstanceOf(com.hemodialyse.backend.contexts.seance.domain.SeanceIllisible.class);
    }
}
