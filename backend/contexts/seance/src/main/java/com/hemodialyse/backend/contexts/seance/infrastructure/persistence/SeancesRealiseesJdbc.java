package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.application.SeancesRealisees;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.util.List;
import java.util.UUID;

/** Les séances réalisées (validées, signées, facturées) du mois sur la table {@code seances}, lecture seule, bornes du mois incluses. */
public class SeancesRealiseesJdbc implements SeancesRealisees {

    private static final String REALISEES = " AND statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')";

    private final JdbcTemplate jdbc;

    public SeancesRealiseesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long nombre(CentreId centre, MoisDuTableauDeBord mois) {
        Long nombre = jdbc.queryForObject("SELECT COUNT(1) FROM seances WHERE center_id = ? AND date_seance BETWEEN ? AND ?" + REALISEES,
                Long.class, centre.valeur(), Date.valueOf(mois.premierJour()), Date.valueOf(mois.dernierJour()));
        return nombre == null ? 0L : nombre;
    }

    @Override
    public List<SeanceRealisee> du(CentreId centre, MoisDuTableauDeBord mois) {
        return jdbc.query("SELECT patient_id, date_seance, statut FROM seances WHERE center_id = ? AND date_seance BETWEEN ? AND ?"
                        + REALISEES,
                (rs, i) -> new SeanceRealisee(new PatientId(rs.getObject("patient_id", UUID.class)), rs.getDate("date_seance").toLocalDate(),
                        rs.getString("statut")),
                centre.valeur(), Date.valueOf(mois.premierJour()), Date.valueOf(mois.dernierJour()));
    }
}
