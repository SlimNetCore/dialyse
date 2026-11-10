package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseFinanciereDesCentres;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lecture JDBC publiée de la part financière de la Facturation (slice F5) : mêmes requêtes que celles des services de la
 * Direction avant leur déplacement. Les centres sont passés en liste de paramètres liés ; une liste vide ne donne rien.
 */
public class SyntheseFinanciereDesCentresJdbc implements SyntheseFinanciereDesCentres {

    private final JdbcTemplate jdbc;

    public SyntheseFinanciereDesCentresJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, ChiffresDuCentre> chiffresParCentre(Collection<UUID> centres, LocalDate du, LocalDate au) {
        Map<UUID, ChiffresDuCentre> resultat = new HashMap<>();
        if (centres.isEmpty()) {
            return resultat;
        }
        String in = marques(centres);
        Map<UUID, BigDecimal> encaisse = new HashMap<>();
        jdbc.query("SELECT f.center_id, COALESCE(SUM(r.montant),0) AS encaisse FROM facture_reglements r "
                        + "INNER JOIN factures f ON f.id = r.facture_id AND f.center_id = r.center_id "
                        + "WHERE f.center_id IN (" + in + ") AND f.date_facturation BETWEEN ? AND ? GROUP BY f.center_id",
                rs -> {
                    encaisse.put(rs.getObject("center_id", UUID.class), rs.getBigDecimal("encaisse"));
                }, parametres(centres, du, au));
        jdbc.query("SELECT center_id, COUNT(*) AS n, COALESCE(SUM(total_ht),0) AS ht, COALESCE(SUM(total_ttc),0) AS ttc "
                        + "FROM factures WHERE center_id IN (" + in + ") AND date_facturation BETWEEN ? AND ? GROUP BY center_id",
                rs -> {
                    UUID centre = rs.getObject("center_id", UUID.class);
                    resultat.put(centre, new ChiffresDuCentre(rs.getLong("n"), rs.getBigDecimal("ht"), rs.getBigDecimal("ttc"),
                            encaisse.getOrDefault(centre, BigDecimal.ZERO)));
                }, parametres(centres, du, au));
        return resultat;
    }

    @Override
    public List<ChiffresDuMois> chiffresParMois(Collection<UUID> centres, LocalDate du, LocalDate au) {
        List<ChiffresDuMois> resultat = new ArrayList<>();
        if (centres.isEmpty()) {
            return resultat;
        }
        jdbc.query("SELECT center_id, EXTRACT(YEAR FROM date_facturation) AS y, EXTRACT(MONTH FROM date_facturation) AS m, "
                        + "COALESCE(SUM(total_ht),0) AS ht, COALESCE(SUM(total_ttc),0) AS ttc "
                        + "FROM factures WHERE center_id IN (" + marques(centres) + ") AND date_facturation BETWEEN ? AND ? "
                        + "GROUP BY center_id, y, m",
                rs -> {
                    resultat.add(new ChiffresDuMois(rs.getObject("center_id", UUID.class), rs.getInt("y"), rs.getInt("m"),
                            rs.getBigDecimal("ht"), rs.getBigDecimal("ttc")));
                }, parametres(centres, du, au));
        return resultat;
    }

    @Override
    public List<ChiffreDeLaCaisse> chiffreDAffairesParCaisse(Collection<UUID> centres, LocalDate du, LocalDate au) {
        List<ChiffreDeLaCaisse> resultat = new ArrayList<>();
        if (centres.isEmpty()) {
            return resultat;
        }
        jdbc.query("SELECT f.center_id, COALESCE(ca.code, '') AS code, MAX(ca.nom) AS nom, "
                        + "COALESCE(SUM(f.total_ht), 0) AS ht FROM factures f "
                        // KB-SEA-84 (ADR 0118) : agence bornée au centre de la facture, comme le centre payeur des séances et des patients
                        + "LEFT JOIN agence ag ON ag.id = f.agence_id_snapshot AND ag.center_id = f.center_id "
                        + "LEFT JOIN caisse_assurance ca ON ca.id = ag.caisse_id "
                        + "WHERE f.center_id IN (" + marques(centres) + ") AND f.date_facturation BETWEEN ? AND ? "
                        + "GROUP BY f.center_id, COALESCE(ca.code, '')",
                rs -> {
                    resultat.add(new ChiffreDeLaCaisse(rs.getObject("center_id", UUID.class), rs.getString("code"),
                            rs.getString("nom"), rs.getBigDecimal("ht")));
                }, parametres(centres, du, au));
        return resultat;
    }

    private static String marques(Collection<UUID> centres) {
        return String.join(",", java.util.Collections.nCopies(centres.size(), "?"));
    }

    private static Object[] parametres(Collection<UUID> centres, LocalDate du, LocalDate au) {
        List<Object> tous = new ArrayList<>(centres);
        tous.add(Date.valueOf(du));
        tous.add(Date.valueOf(au));
        return tous.toArray();
    }
}
