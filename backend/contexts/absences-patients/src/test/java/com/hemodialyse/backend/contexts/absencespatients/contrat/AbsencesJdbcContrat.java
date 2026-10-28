package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.AbsencesJdbc;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Contrat des absences contre l'adaptateur JDBC : H2 (table minimale) et PostgreSQL 16 (schéma Flyway).
 */
abstract class AbsencesJdbcContrat extends AbsencesContrat {

    protected abstract JdbcTemplate jdbc();

    @Override
    protected AbsencesRepository absences() {
        return new AbsencesJdbc(jdbc());
    }

    /** Table créée à l'identique de la production (H2 seulement : PostgreSQL a le schéma Flyway). */
    protected void creerLaTable() {
        jdbc().execute("CREATE TABLE IF NOT EXISTS absence_patient (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "patient_id UUID NOT NULL, date_seance DATE NOT NULL, source VARCHAR(20) NOT NULL, "
                + "statut VARCHAR(20) NOT NULL, motif VARCHAR(30), commentaire VARCHAR(1000), forfait_id UUID, "
                + "forfait_libelle VARCHAR(255), prix_ttc NUMERIC(14,2) NOT NULL DEFAULT 0, "
                + "taux_tva NUMERIC(6,2) NOT NULL DEFAULT 0, montant_ht NUMERIC(14,2) NOT NULL DEFAULT 0, declaree_par UUID, "
                + "declaree_le TIMESTAMP WITH TIME ZONE, qualifiee_par UUID, qualifiee_le TIMESTAMP WITH TIME ZONE, "
                + "date_rattrapage DATE, modifiee_par UUID, modifiee_le TIMESTAMP WITH TIME ZONE, "
                + "UNIQUE (center_id, patient_id, date_seance))");
    }

    /** Vide les lignes des centres du contrat. */
    protected void viderLesLignesDeTest() {
        for (CentreId centre : new CentreId[]{CENTRE, AUTRE_CENTRE}) {
            jdbc().update("DELETE FROM absence_patient WHERE center_id = ?", centre.valeur());
        }
    }
}
