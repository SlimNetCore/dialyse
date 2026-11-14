package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.PoidsSecsPrescritsJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.PoidsSecsPrescritsPublies;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Partie JDBC du contrat des poids secs prescrits (H2 et PostgreSQL 16 : seule la connexion change). Lignes des centres A et B, effacées avant et après. */
abstract class PoidsSecsPrescritsJdbcContrat extends PoidsSecsPrescritsContrat {

    protected abstract JdbcTemplate jdbc();

    protected void vider() {
        jdbc().update("DELETE FROM prescriptions_medicales WHERE center_id IN (?, ?)", A, B);
    }

    @Override
    protected PoidsSecsPrescritsPublies port() {
        return new PoidsSecsPrescritsJdbc(jdbc());
    }

    @Override
    protected void prescription(UUID id, UUID centre, UUID patient, LocalDate date, BigDecimal poidsSec, Instant majLe) {
        jdbc().update("INSERT INTO prescriptions_medicales (id, center_id, patient_id, date_prescription, poids_sec_cible_kg, created_at, "
                        + "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)", id, centre, patient, Date.valueOf(date), poidsSec,
                Timestamp.from(Instant.parse("2026-01-01T10:00:00Z")), majLe == null ? null : Timestamp.from(majLe));
    }
}
