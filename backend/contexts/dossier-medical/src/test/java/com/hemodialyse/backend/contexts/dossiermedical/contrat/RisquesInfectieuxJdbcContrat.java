package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.PatientsARisqueInfectieuxJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.RisquesInfectieuxPublies;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/** Contrat du risque infectieux sur la table réelle {@code serologies_patient} (H2 et PostgreSQL 16 : sous-classes). */
abstract class RisquesInfectieuxJdbcContrat extends RisquesInfectieuxContrat {

    protected abstract JdbcTemplate jdbc();

    protected void vider() {
        for (Object centre : new Object[]{CENTRE_A, CENTRE_B}) {
            jdbc().update("DELETE FROM serologies_patient WHERE center_id = ?", centre);
        }
    }

    @Override
    protected RisquesInfectieuxPublies risques() {
        return new PatientsARisqueInfectieuxJdbc(jdbc());
    }

    @Override
    protected void serologie(UUID centre, UUID patient, String marqueur, String resultat, LocalDate prelevement, Instant saisie) {
        jdbc().update("INSERT INTO serologies_patient (id, center_id, patient_id, marqueur, resultat, date_prelevement, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID(), centre, patient, marqueur, resultat, Date.valueOf(prelevement),
                saisie.atOffset(ZoneOffset.UTC), saisie.atOffset(ZoneOffset.UTC));
    }
}
