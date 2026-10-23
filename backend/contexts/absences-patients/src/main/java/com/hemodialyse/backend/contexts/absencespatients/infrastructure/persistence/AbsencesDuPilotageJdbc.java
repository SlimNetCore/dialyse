package com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence;

import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Lectures du pilotage sur {@code absence_patient}, reprises à l'identique du legacy (slice A4) : mêmes requêtes que le tableau de
 * bord des séances et la vue Direction. Seule différence décidée (KB-ABS-34) : « aujourd'hui » suit le fuseau des centres.
 */
public class AbsencesDuPilotageJdbc implements AbsencesDuPilotage {

    private final JdbcTemplate jdbc;
    private final Clock horloge;
    private final ZoneId fuseau;

    public AbsencesDuPilotageJdbc(JdbcTemplate jdbc, Clock horloge, ZoneId fuseau) {
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.fuseau = fuseau;
    }

    private static String marqueurs(Collection<UUID> centres) {
        return String.join(",", centres.stream().map(c -> "?").toList());
    }

    private static Object[] parametres(Collection<UUID> centres, LocalDate du, LocalDate au) {
        List<Object> p = new ArrayList<>(centres);
        p.add(Date.valueOf(du));
        p.add(Date.valueOf(au));
        return p.toArray();
    }

    @Override
    public LocalDate aujourdhui() {
        return LocalDate.now(horloge.withZone(fuseau));
    }

    @Override
    public long nombreHorsAnnulees(UUID centre, LocalDate du, LocalDate au) {
        Long n = jdbc.queryForObject("SELECT COUNT(1) FROM absence_patient WHERE center_id = ? AND date_seance BETWEEN ? AND ? "
                + "AND statut <> 'ANNULEE'", Long.class, centre, Date.valueOf(du), Date.valueOf(au));
        return n == null ? 0L : n;
    }

    @Override
    public List<AbsenceDuCentre> horsAnnulees(UUID centre, LocalDate du, LocalDate au) {
        return jdbc.query("SELECT s.patient_id, s.date_seance, s.statut FROM absence_patient s WHERE s.center_id = ? "
                        + "AND s.date_seance BETWEEN ? AND ? AND s.statut <> 'ANNULEE'",
                (rs, i) -> new AbsenceDuCentre(rs.getObject("patient_id", UUID.class), rs.getDate("date_seance").toLocalDate(),
                        rs.getString("statut")),
                centre, Date.valueOf(du), Date.valueOf(au));
    }

    @Override
    public List<CumulDuCentre> cumulParCentre(Collection<UUID> centres, LocalDate du, LocalDate au) {
        if (centres.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT center_id, statut, motif, COUNT(*) AS n, COALESCE(SUM(montant_ht),0) AS ht, "
                        + "COALESCE(SUM(prix_ttc),0) AS ttc FROM absence_patient WHERE center_id IN (" + marqueurs(centres)
                        + ") AND date_seance BETWEEN ? AND ? AND statut <> 'ANNULEE' GROUP BY center_id, statut, motif",
                (rs, i) -> new CumulDuCentre(rs.getObject("center_id", UUID.class), rs.getString("statut"), rs.getString("motif"),
                        rs.getLong("n"), rs.getBigDecimal("ht"), rs.getBigDecimal("ttc")),
                parametres(centres, du, au));
    }

    @Override
    public List<AbsencesDuMois> mensuel(Collection<UUID> centres, LocalDate du, LocalDate au) {
        if (centres.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT EXTRACT(YEAR FROM date_seance) AS y, EXTRACT(MONTH FROM date_seance) AS m, COUNT(*) AS n, "
                        + "COALESCE(SUM(montant_ht),0) AS ht FROM absence_patient WHERE center_id IN (" + marqueurs(centres)
                        + ") AND date_seance BETWEEN ? AND ? AND statut IN ('A_QUALIFIER','JUSTIFIEE','NON_JUSTIFIEE') "
                        + "GROUP BY y, m ORDER BY y, m",
                (rs, i) -> new AbsencesDuMois(rs.getInt("y"), rs.getInt("m"), rs.getLong("n"), rs.getBigDecimal("ht")),
                parametres(centres, du, au));
    }
}
