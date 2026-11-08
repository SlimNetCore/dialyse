package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Constantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.HistoriqueDesConstantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SeanceId;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.seance.HistoriqueDesConstantesPublie;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.ConstantesDesSeancesJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contrat du port sur les tables réelles {@code seances} et {@code volet_paramedical} (H2 et PostgreSQL 16 : sous-classes), joué depuis
 * SE7d par l'ACL {@code HistoriqueDesConstantesPublie} au-dessus de l'adapter publié de la Séance ({@code ConstantesDesSeancesJdbc}).
 */
abstract class HistoriqueDesConstantesJdbcContrat extends HistoriqueDesConstantesContrat {

    protected abstract JdbcTemplate jdbc();

    /** Schéma H2 des deux tables lues (colonnes lues et colonnes obligatoires du schéma réel). */
    static void creerLesTables(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS seances (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE, date_seance DATE NOT NULL, patient_id UUID NOT NULL, "
                + "statut VARCHAR(255) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS volet_paramedical (id UUID PRIMARY KEY, anticoagulant VARCHAR(100), "
                + "center_id UUID NOT NULL, created_at TIMESTAMP WITH TIME ZONE, debit_sang_ml_min INTEGER, duree_minutes INTEGER, "
                + "incidents TEXT, poids_apres_kg NUMERIC(38, 2), poids_avant_kg NUMERIC(38, 2), seance_id UUID NOT NULL UNIQUE, "
                + "ta_apres VARCHAR(30), ta_avant VARCHAR(30), type_dialysat VARCHAR(100), ultrafiltration_ml NUMERIC(38, 2), "
                + "updated_at TIMESTAMP WITH TIME ZONE)");
    }

    /** Vide les lignes de test (avant et après chaque test). */
    protected void vider() {
        for (Object centre : new Object[]{CENTRE_A.valeur(), CENTRE_B.valeur()}) {
            jdbc().update("DELETE FROM volet_paramedical WHERE center_id = ?", centre);
            jdbc().update("DELETE FROM seances WHERE center_id = ?", centre);
        }
    }

    @Override
    protected Banc nouveauBanc() {
        vider();
        JdbcTemplate jdbc = jdbc();
        // SE7d (ADR 0098) : l'ACL de production au-dessus du VRAI adapter publié de la Séance (dépendance directe, sans cycle)
        HistoriqueDesConstantes historique = new HistoriqueDesConstantesPublie(new ConstantesDesSeancesJdbc(jdbc));
        return new Banc() {
            @Override
            public void seance(CentreId centre, PatientId patient, SeanceId seance, LocalDate date, Instant creeLe) {
                jdbc.update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut, created_at) VALUES (?,?,?,?,?,?)",
                        seance.valeur(), centre.valeur(), patient.valeur(), java.sql.Date.valueOf(date), "VALIDEE", Timestamp.from(creeLe));
            }

            @Override
            public void volet(CentreId centre, SeanceId seance, Constantes c) {
                jdbc.update("DELETE FROM volet_paramedical WHERE seance_id = ?", seance.valeur());
                jdbc.update("INSERT INTO volet_paramedical (id, center_id, seance_id, poids_avant_kg, poids_apres_kg, ta_avant, ta_apres, "
                                + "debit_sang_ml_min, ultrafiltration_ml, duree_minutes) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        UUID.randomUUID(), centre.valeur(), seance.valeur(), c.poidsAvantKg(), c.poidsApresKg(), c.tensionAvant(),
                        c.tensionApres(), c.debitSangMlMin(), c.ultrafiltrationMl(), c.dureeMinutes());
            }

            @Override
            public HistoriqueDesConstantes historique() {
                return historique;
            }
        };
    }
}
