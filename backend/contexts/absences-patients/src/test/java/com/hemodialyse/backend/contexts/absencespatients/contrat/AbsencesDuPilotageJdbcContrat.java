package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.AbsencesDuPilotageJdbc;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Contrat des lectures du pilotage contre l'adaptateur JDBC : H2 (table minimale) et PostgreSQL 16 (schéma Flyway). Les insertions
 * n'utilisent que des colonnes qui existent dans les deux.
 */
abstract class AbsencesDuPilotageJdbcContrat extends AbsencesDuPilotageContrat {

    protected abstract JdbcTemplate jdbc();

    @Override
    protected AbsencesDuPilotage pilotage() {
        return new AbsencesDuPilotageJdbc(jdbc(), Clock.systemUTC(), ZoneId.of("Africa/Algiers"));
    }

    protected void viderLesLignesDeTest() {
        for (UUID centre : new UUID[]{CENTRE, AUTRE_CENTRE, TROISIEME_CENTRE}) {
            jdbc().update("DELETE FROM absence_patient WHERE center_id = ?", centre);
        }
    }

    @Override
    protected void poser(UUID centre, UUID patient, LocalDate date, String statut, String motif, BigDecimal ht, BigDecimal ttc) {
        jdbc().update("INSERT INTO absence_patient (id, center_id, patient_id, date_seance, source, statut, motif, prix_ttc, "
                        + "taux_tva, montant_ht) VALUES (?, ?, ?, ?, 'AUTOMATIQUE', ?, ?, ?, 19.00, ?)",
                UUID.randomUUID(), centre, patient, Date.valueOf(date), statut, motif, ttc, ht);
    }
}
