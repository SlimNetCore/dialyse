package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FacturationDuPatient;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lecture JDBC publiée : une facture du patient couvre-t-elle le jour (bornes incluses, tout statut) ?
 */
public class FacturationDuPatientJdbc implements FacturationDuPatient {

    private final JdbcTemplate jdbc;

    public FacturationDuPatientJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean periodeFacturee(UUID centre, UUID patient, LocalDate jour) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM factures WHERE center_id = ? AND patient_id = ? "
                + "AND period_start <= ? AND period_end >= ?", Integer.class, centre, patient, Date.valueOf(jour),
                Date.valueOf(jour));
        return n != null && n > 0;
    }
}
