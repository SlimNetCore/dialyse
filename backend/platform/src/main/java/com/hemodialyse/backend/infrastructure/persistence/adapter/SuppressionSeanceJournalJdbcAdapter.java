package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.model.SuppressionSeance;
import com.hemodialyse.backend.domain.seance.port.SuppressionSeanceJournalPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * Journal des séances supprimées (table {@code seance_suppression}), bornée au centre de la séance.
 */
@Component
public class SuppressionSeanceJournalJdbcAdapter implements SuppressionSeanceJournalPort {

    private final JdbcTemplate jdbc;

    public SuppressionSeanceJournalJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrer(SuppressionSeance s) {
        jdbc.update("INSERT INTO seance_suppression (id, center_id, seance_id, patient_id, date_seance, statut, motif, "
                        + "supprime_par, supprime_le) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                s.id(), s.centerId(), s.seanceId(), s.patientId(), Date.valueOf(s.dateSeance()), s.statut().name(),
                s.motif().valeur(), s.supprimePar(), Timestamp.from(s.supprimeLe()));
    }
}
