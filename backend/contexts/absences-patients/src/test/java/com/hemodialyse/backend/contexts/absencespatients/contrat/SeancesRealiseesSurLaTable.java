package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.SeancesRealiseesFournies;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Double de TEST du langage fourni {@link SeancesRealiseesFournies} : il lit la table des séances que les tests des Absences sèment eux-mêmes
 * (le module ne peut pas dépendre de la Séance, ADR 0098). Il n'existe que pour garder ces tests sur les tables réelles (H2, PostgreSQL 16)
 * des Absences ; le vrai adapter de la Séance et sa sémantique (casse, bornes, centre, doublons) sont prouvés par
 * {@code seance/.../contrat/SeancesRealiseesEtConstantesContrat} (mémoire, H2, PG16) et par les goldens SE7d et A1 à A4 de la plateforme,
 * câblés en production.
 */
public final class SeancesRealiseesSurLaTable implements SeancesRealiseesFournies {

    private static final String REALISEE = "statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')";

    private final JdbcTemplate jdbc;

    public SeancesRealiseesSurLaTable(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<JourRealiseFourni> joursRealisesDuCentre(UUID centre, LocalDate du, LocalDate au) {
        return jdbc.query("SELECT DISTINCT patient_id, date_seance FROM seances WHERE center_id = ? AND date_seance BETWEEN ? AND ? AND "
                        + REALISEE + " ORDER BY date_seance",
                (rs, i) -> new JourRealiseFourni(rs.getObject(1, UUID.class), rs.getDate(2).toLocalDate()), centre, Date.valueOf(du),
                Date.valueOf(au));
    }

    @Override
    public List<LocalDate> joursRealisesDuPatient(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return jdbc.query("SELECT DISTINCT date_seance FROM seances WHERE center_id = ? AND patient_id = ? AND date_seance BETWEEN ? AND ? AND "
                + REALISEE + " ORDER BY date_seance", (rs, i) -> rs.getDate(1).toLocalDate(), centre, patient, Date.valueOf(du), Date.valueOf(au));
    }

    @Override
    public boolean estRealisee(UUID centre, UUID patient, LocalDate jour) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE center_id = ? AND patient_id = ? AND date_seance = ? AND " + REALISEE,
                Integer.class, centre, patient, Date.valueOf(jour));
        return n != null && n > 0;
    }

    @Override
    public List<UUID> patientsRealisesLe(UUID centre, LocalDate jour) {
        return jdbc.queryForList("SELECT patient_id FROM seances WHERE center_id = ? AND date_seance = ? AND " + REALISEE, UUID.class, centre,
                Date.valueOf(jour));
    }
}
