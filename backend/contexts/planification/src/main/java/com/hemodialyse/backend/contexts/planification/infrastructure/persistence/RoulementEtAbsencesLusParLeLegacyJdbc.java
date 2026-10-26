package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.RoulementEtAbsencesDesInfirmiers;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lecture publiée du roulement, des absences et des remplacements (tables {@code infirmier_affectation}, {@code infirmier_absence},
 * {@code infirmier_remplacement}) : les mêmes lignes que celles lues avant la refonte par la présence, traduites en records publiés.
 */
public class RoulementEtAbsencesLusParLeLegacyJdbc implements RoulementEtAbsencesDesInfirmiers {
    private final JdbcTemplate jdbc;

    public RoulementEtAbsencesLusParLeLegacyJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Affectation> affectationsDesInfirmiersActifs(UUID centre) {
        return jdbc.query("SELECT a.id, a.infirmier_id, a.salle_id, a.creneau_id, a.jours "
                        + "FROM infirmier_affectation a JOIN infirmier i ON i.id = a.infirmier_id AND i.center_id = a.center_id "
                        + "WHERE a.center_id = ? AND i.actif = TRUE",
                (rs, i) -> {
                    Set<DayOfWeek> jours = EnumSet.noneOf(DayOfWeek.class);
                    RoulementsJdbc.decoder(rs.getString("jours")).forEach(j -> jours.add(j.jourCivil()));
                    return new Affectation(rs.getObject("id", UUID.class), rs.getObject("infirmier_id", UUID.class),
                            rs.getObject("salle_id", UUID.class), rs.getObject("creneau_id", UUID.class), jours);
                }, centre);
    }

    @Override
    public List<Absence> absencesSurLaPeriode(UUID centre, LocalDate du, LocalDate au) {
        return jdbc.query("SELECT id, infirmier_id, date_debut, date_fin, type, motif FROM infirmier_absence "
                        + "WHERE center_id = ? AND date_debut <= ? AND date_fin >= ?",
                (rs, i) -> new Absence(rs.getObject("id", UUID.class), rs.getObject("infirmier_id", UUID.class),
                        rs.getDate("date_debut").toLocalDate(), rs.getDate("date_fin").toLocalDate(), rs.getString("type"),
                        rs.getString("motif")), centre, Date.valueOf(au), Date.valueOf(du));
    }

    @Override
    public List<Remplacement> remplacementsSurLaPeriode(UUID centre, LocalDate du, LocalDate au) {
        return jdbc.query("SELECT id, date_jour, salle_id, creneau_id, infirmier_id, remplace_infirmier_id "
                        + "FROM infirmier_remplacement WHERE center_id = ? AND date_jour BETWEEN ? AND ?",
                (rs, i) -> new Remplacement(rs.getObject("id", UUID.class), rs.getDate("date_jour").toLocalDate(),
                        rs.getObject("salle_id", UUID.class), rs.getObject("creneau_id", UUID.class),
                        rs.getObject("infirmier_id", UUID.class), rs.getObject("remplace_infirmier_id", UUID.class)),
                centre, Date.valueOf(du), Date.valueOf(au));
    }
}
