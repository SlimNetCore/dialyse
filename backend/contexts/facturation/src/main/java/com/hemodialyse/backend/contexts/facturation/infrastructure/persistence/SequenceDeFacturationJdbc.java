package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SequenceDeFacturation;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Séquence de numérotation des factures : table {@code facture_sequence} (une ligne par centre et par année). Lecture seule en
 * F2 ; la réservation d'un numéro est celle de l'émission (F3).
 */
public class SequenceDeFacturationJdbc implements SequenceDeFacturation {

    private final JdbcTemplate jdbc;

    public SequenceDeFacturationJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int courante(CentreId centre, int annee) {
        return jdbc.queryForList("SELECT seq_value FROM facture_sequence WHERE center_id = ? AND seq_year = ?", Integer.class,
                centre.valeur(), annee).stream().findFirst().orElse(0);
    }
}
