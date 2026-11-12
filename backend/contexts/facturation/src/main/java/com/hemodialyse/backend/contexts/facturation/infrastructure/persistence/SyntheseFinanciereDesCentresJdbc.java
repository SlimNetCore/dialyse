package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseFinanciereDesCentres;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lecture JDBC publiée de la part financière de la Facturation (slice F5) : mêmes requêtes que celles des services de la
 * Direction avant leur déplacement. Les centres sont passés en liste de paramètres liés ; une liste vide ne donne rien.
 */
public class SyntheseFinanciereDesCentresJdbc implements SyntheseFinanciereDesCentres {

    private final JdbcTemplate jdbc;

    private final PayeursDuCentre annuaire;

    public SyntheseFinanciereDesCentresJdbc(JdbcTemplate jdbc, PayeursDuCentre annuaire) {
        this.jdbc = jdbc;
        this.annuaire = annuaire;
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
        // PL8b : montant par (centre, agence figée) lu sur les factures ; la caisse vient de l'annuaire des payeurs.
        record MontantDeLAgence(UUID centre, UUID agence, BigDecimal ht) {
        }
        List<MontantDeLAgence> montants = new ArrayList<>();
        jdbc.query("SELECT center_id, agence_id_snapshot, COALESCE(SUM(total_ht), 0) AS ht FROM factures "
                        + "WHERE center_id IN (" + marques(centres) + ") AND date_facturation BETWEEN ? AND ? "
                        + "GROUP BY center_id, agence_id_snapshot",
                rs -> {
                    montants.add(new MontantDeLAgence(rs.getObject("center_id", UUID.class), rs.getObject("agence_id_snapshot", UUID.class),
                            rs.getBigDecimal("ht")));
                }, parametres(centres, du, au));
        // KB-SEA-84 (ADR 0118) : agence bornée au centre de la facture ; la caisse de cette agence, elle, n'est pas bornée (KB-RF3-07)
        Map<UUID, Set<UUID>> agencesDuCentre = new HashMap<>();
        for (UUID centre : centres) {
            Set<UUID> ids = new HashSet<>();
            annuaire.agences(centre).forEach(a -> ids.add(a.id()));
            agencesDuCentre.put(centre, ids);
        }
        Set<UUID> aResoudre = new HashSet<>();
        for (MontantDeLAgence m : montants) {
            if (m.agence() != null && agencesDuCentre.getOrDefault(m.centre(), Set.of()).contains(m.agence())) aResoudre.add(m.agence());
        }
        Map<UUID, PayeursDuCentre.CaissePubliee> caisses = annuaire.caissesDesAgences(aResoudre);
        record Cle(UUID centre, String code) {
        }
        Map<Cle, String> noms = new LinkedHashMap<>();
        Map<Cle, BigDecimal> totaux = new LinkedHashMap<>();
        for (MontantDeLAgence m : montants) {
            PayeursDuCentre.CaissePubliee caisse = aResoudre.contains(m.agence()) && agencesDuCentre.get(m.centre()).contains(m.agence())
                    ? caisses.get(m.agence()) : null;
            Cle cle = new Cle(m.centre(), caisse == null || caisse.code() == null ? "" : caisse.code());
            totaux.merge(cle, m.ht() == null ? BigDecimal.ZERO : m.ht(), BigDecimal::add);
            String nom = caisse == null ? null : caisse.nom();
            noms.merge(cle, nom == null ? "" : nom, (a, b) -> a.compareTo(b) >= 0 ? a : b);
        }
        totaux.forEach((cle, ht) -> {
            String nom = noms.get(cle);
            resultat.add(new ChiffreDeLaCaisse(cle.centre(), cle.code(), nom == null || nom.isEmpty() ? null : nom, ht));
        });
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
