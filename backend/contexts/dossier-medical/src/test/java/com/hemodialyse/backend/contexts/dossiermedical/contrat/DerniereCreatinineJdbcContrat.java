package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.DerniereCreatinineJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DerniereCreatininePubliee;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Contrat de la dernière créatinine sur la table réelle (H2 et PostgreSQL 16 : sous-classes). */
abstract class DerniereCreatinineJdbcContrat extends DerniereCreatinineContrat {
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    protected abstract JdbcTemplate jdbc();

    protected void vider() {
        for (Object centre : new Object[]{CENTRE_A, CENTRE_B}) {
            jdbc().update("DELETE FROM resultats_analyses WHERE center_id = ?", centre);
        }
    }

    @Override
    protected DerniereCreatininePubliee creatinine() {
        return new DerniereCreatinineJdbc(jdbc());
    }

    @Override
    protected void analyse(UUID centre, LocalDate prelevement, String creatinine) {
        jdbc().update("INSERT INTO resultats_analyses (id, patient_id, center_id, date_prelevement, creatinine_mg_dl, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?)", UUID.randomUUID(), PATIENT, centre, Date.valueOf(prelevement),
                creatinine == null ? null : new BigDecimal(creatinine), T0, T0);
    }
}
