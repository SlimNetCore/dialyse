package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lecture publiée des séances réalisées des centres pour le pilotage de la Direction (ADR 0099, SE7e), lecture seule. Requêtes reprises
 * MOT POUR MOT de la plateforme : E1 et E5 ({@code DirectionDashboardQueryService.overview}, {@code DirectionAbsencesQueryService.absences},
 * texte identique), E2 ({@code previousPeriodTotals}), E3 ({@code monthly}), E4 ({@code DirectionBreakdownQueryService.loadCaisseSeances},
 * avec sa jointure {@code patients} sur l'identifiant ET le centre et sa chaîne payeur / agence / caisse sans centre : KB-SEA-83, 84).
 * Paramètres de date passés en {@link Date} comme avant.
 */
public class SeancesRealiseesDesCentresJdbc implements SeancesRealiseesDesCentresPubliees {

    private static final String CAISSE_JOIN =
            "LEFT JOIN centre_payeur cp ON cp.id = p.centre_payeur_id "
                    + "LEFT JOIN agence ag ON ag.id = cp.agence_id "
                    + "LEFT JOIN caisse_assurance ca ON ca.id = ag.caisse_id ";

    private final JdbcTemplate jdbc;

    public SeancesRealiseesDesCentresJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String placeholders(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }

    private static Object[] params(List<UUID> ids, LocalDate du, LocalDate au) {
        List<Object> all = new ArrayList<>(ids);
        all.add(Date.valueOf(du));
        all.add(Date.valueOf(au));
        return all.toArray();
    }

    @Override
    public Map<UUID, Long> parCentre(List<UUID> centres, LocalDate du, LocalDate au) {
        Map<UUID, Long> seances = new HashMap<>();
        jdbc.query("SELECT center_id, COUNT(*) AS n FROM seances WHERE center_id IN (" + placeholders(centres.size()) + ") "
                        + "AND date_seance BETWEEN ? AND ? AND statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY center_id",
                rs -> {
                    seances.put(rs.getObject("center_id", UUID.class), rs.getLong("n"));
                }, params(centres, du, au));
        return seances;
    }

    @Override
    public long auTotal(List<UUID> centres, LocalDate du, LocalDate au) {
        Long seances = jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE center_id IN (" + placeholders(centres.size()) + ") "
                        + "AND date_seance BETWEEN ? AND ? AND statut IN ('VALIDEE','SIGNEE','FACTUREE')",
                Long.class, params(centres, du, au));
        return seances == null ? 0 : seances;
    }

    @Override
    public List<MoisRealisePublie> parMois(List<UUID> centres, LocalDate du, LocalDate au) {
        List<MoisRealisePublie> mois = new ArrayList<>();
        jdbc.query("SELECT center_id, EXTRACT(YEAR FROM date_seance) AS y, EXTRACT(MONTH FROM date_seance) AS m, COUNT(*) AS n "
                        + "FROM seances WHERE center_id IN (" + placeholders(centres.size()) + ") AND date_seance BETWEEN ? AND ? "
                        + "AND statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY center_id, y, m",
                rs -> {
                    mois.add(new MoisRealisePublie(rs.getObject("center_id", UUID.class), rs.getInt("y"), rs.getInt("m"), rs.getLong("n")));
                }, params(centres, du, au));
        return mois;
    }

    @Override
    public List<CaisseRealiseePubliee> parCaisse(List<UUID> centres, LocalDate du, LocalDate au) {
        List<CaisseRealiseePubliee> caisses = new ArrayList<>();
        jdbc.query("SELECT s.center_id, COALESCE(ca.code, '') AS code, MAX(ca.nom) AS nom, COUNT(*) AS n "
                        + "FROM seances s INNER JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id "
                        + CAISSE_JOIN + "WHERE s.center_id IN (" + placeholders(centres.size()) + ") AND s.date_seance BETWEEN ? AND ? "
                        + "AND s.statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY s.center_id, COALESCE(ca.code, '')",
                rs -> {
                    caisses.add(new CaisseRealiseePubliee(rs.getObject("center_id", UUID.class), rs.getString("code"), rs.getString("nom"),
                            rs.getLong("n")));
                }, params(centres, du, au));
        return caisses;
    }
}
