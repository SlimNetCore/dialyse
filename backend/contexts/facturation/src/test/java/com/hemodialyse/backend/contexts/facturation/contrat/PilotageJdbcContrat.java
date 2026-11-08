package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDuTableauDeBord;
import com.hemodialyse.backend.contexts.facturation.doubles.PilotageEnMemoire.Facture;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.LectureDeLaSyntheseJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.LectureDuTableauDeBordJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.SyntheseFinanciereDesCentresJdbc;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseFinanciereDesCentres;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Le contrat des lectures de pilotage exécuté contre les adaptateurs JDBC. Les variantes H2 et PostgreSQL 16 ne diffèrent que
 * par leur base ; les lignes de test sont semées dans les tables du schéma figé et supprimées avant chaque test.
 */
public abstract class PilotageJdbcContrat extends PilotageContrat {

    private int rang;

    protected abstract JdbcTemplate jdbc();

    protected void viderLesLignesDeTest() {
        rang = 0;
        for (String table : new String[]{"facture_reglements", "seances", "factures", "agence", "caisse_assurance"}) {
            jdbc().update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE_A.valeur(), CENTRE_B.valeur());
        }
    }

    @Override
    protected LectureDuTableauDeBord tableau() {
        return new LectureDuTableauDeBordJdbc(jdbc(), new SeancesDesFacturesSurLaTable(jdbc()));
    }

    @Override
    protected LectureDeLaSynthese synthese() {
        return new LectureDeLaSyntheseJdbc(jdbc(), new SeancesDesFacturesSurLaTable(jdbc()));
    }

    @Override
    protected SyntheseFinanciereDesCentres finance() {
        return new SyntheseFinanciereDesCentresJdbc(jdbc());
    }

    private static UUID derive(String cle) {
        return UUID.nameUUIDFromBytes(cle.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void poserLaFacture(Facture f) {
        UUID agence = null;
        if (f.codeCaisse() != null) {
            UUID caisse = derive("caisse:" + f.centre() + ":" + f.codeCaisse());
            agence = derive("agence:" + f.centre() + ":" + f.codeCaisse());
            if (jdbc().queryForObject("SELECT COUNT(*) FROM caisse_assurance WHERE id = ?", Integer.class, caisse) == 0) {
                jdbc().update("INSERT INTO caisse_assurance (id, center_id, code, nom) VALUES (?, ?, ?, ?)", caisse, f.centre(),
                        f.codeCaisse(), f.nomCaisse());
                jdbc().update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, ?, ?)", agence, f.centre(),
                        caisse, "AG-" + f.codeCaisse(), "Agence " + f.codeCaisse());
            }
        }
        rang++;
        Date jour = Date.valueOf(f.date());
        jdbc().update("INSERT INTO factures (id, center_id, patient_id, numero_facture, patient_status_snapshot, agence_id_snapshot, "
                        + "period_start, period_end, date_facturation, tva_rate, total_ht, total_tva, total_ttc) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)",
                f.id(), f.centre(), f.patient(), "FAC-F5-" + rang + "-" + f.id(), f.situation(), agence, jour, jour, jour, f.ht(),
                f.tva(), f.ttc());
    }

    @Override
    protected void poserLaSeance(UUID centre, UUID facture, LocalDate date, String statut) {
        jdbc().update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut, facture_id) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), centre, PATIENT_1, Date.valueOf(date), statut, facture);
    }

    @Override
    protected void poserLeReglement(UUID centre, UUID facture, String montant) {
        jdbc().update("INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), facture, centre, new BigDecimal(montant), Date.valueOf(LocalDate.of(2026, 4, 1)));
    }
}
