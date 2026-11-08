package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.application.JournalDesSuppressions;
import com.hemodialyse.backend.contexts.seance.domain.SuppressionDeSeance;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Le journal des séances supprimées sur la table RÉELLE {@code seance_suppression} (V27, aucune colonne ajoutée). Une ligne par
 * suppression ; le lien avec la facture n'y est pas gardé (reproduit : c'est la raison du refus des séances rattachées à une facture).
 */
public class JournalDesSuppressionsJdbc implements JournalDesSuppressions {

    private final JdbcTemplate jdbc;

    public JournalDesSuppressionsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrer(SuppressionDeSeance s) {
        jdbc.update("INSERT INTO seance_suppression (id, center_id, seance_id, patient_id, date_seance, statut, motif, supprime_par, "
                        + "supprime_le) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", s.id(), s.centre().valeur(), s.seance().valeur(),
                s.patient().valeur(), Date.valueOf(s.jour()), s.statut().name(), s.motif().valeur(), s.auteur(),
                OffsetDateTime.ofInstant(s.supprimeLe(), ZoneOffset.UTC));
    }
}
