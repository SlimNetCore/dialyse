package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.domain.ChaineDesPayeursDuCentre;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lecture publiée des séances réalisées des centres pour le pilotage de la Direction (ADR 0099, SE7e), lecture seule. Requêtes reprises
 * MOT POUR MOT de la plateforme : E1 et E5 ({@code DirectionDashboardQueryService.overview}, {@code DirectionAbsencesQueryService.absences},
 * texte identique), E2 ({@code previousPeriodTotals}), E3 ({@code monthly}), E4 ({@code DirectionBreakdownQueryService.loadCaisseSeances},
 * avec sa jointure {@code patients} sur l'identifiant ET le centre).
 * Paramètres de date passés en {@link Date} comme avant. Corrigé le 2026-11-10 (lot L5b, ADR 0118) : UN prédicat pour les trois vues, celui de la
 * séance elle-même : la répartition par caisse joint la fiche en {@code LEFT JOIN} (une séance sur une fiche d'un autre centre reste comptée sous
 * la caisse vide : KB-DIR-05, KB-SEA-83) et le centre payeur est borné au centre de la fiche (KB-SEA-84).
 */
public class SeancesRealiseesDesCentresJdbc implements SeancesRealiseesDesCentresPubliees {

    private final JdbcTemplate jdbc;
    private final ChaineDesPayeursDuCentre payeurs;

    /** {@code payeurs} : la chaîne des payeurs de Patient et couverture (borne du centre payeur au centre de la fiche : KB-SEA-84, ADR 0118). */
    public SeancesRealiseesDesCentresJdbc(JdbcTemplate jdbc, ChaineDesPayeursDuCentre payeurs) {
        this.jdbc = jdbc;
        this.payeurs = payeurs;
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

    /**
     * Séances par caisse. La fiche est jointe en {@code LEFT JOIN} (centre de la séance) et regroupée par centre payeur ; la caisse du centre
     * payeur vient de l'annuaire des payeurs (RF3b, ADR 0127), bornée au centre, et les compteurs sont additionnés par code de caisse
     * (code vide sans caisse, nom nul sans caisse, plus grand nom sinon : comme le {@code MAX(ca.nom)} d'avant).
     */
    @Override
    public List<CaisseRealiseePubliee> parCaisse(List<UUID> centres, LocalDate du, LocalDate au) {
        record Cle(UUID centre, String code) {
        }
        List<Object[]> lignes = new ArrayList<>();
        jdbc.query("SELECT s.center_id, p.centre_payeur_id, COUNT(*) AS n "
                        + "FROM seances s LEFT JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id "
                        + "WHERE s.center_id IN (" + placeholders(centres.size()) + ") AND s.date_seance BETWEEN ? AND ? "
                        + "AND s.statut IN ('VALIDEE','SIGNEE','FACTUREE') GROUP BY s.center_id, p.centre_payeur_id",
                rs -> {
                    lignes.add(new Object[]{rs.getObject("center_id", UUID.class), rs.getObject("centre_payeur_id", UUID.class),
                            rs.getLong("n")});
                }, params(centres, du, au));
        Map<UUID, Map<UUID, ChaineDesPayeursDuCentre.CaisseDuPayeur>> caissesParCentre = new HashMap<>();
        Map<Cle, long[]> nombres = new LinkedHashMap<>();
        Map<Cle, String> noms = new HashMap<>();
        for (Object[] ligne : lignes) {
            UUID centre = (UUID) ligne[0];
            UUID centrePayeur = (UUID) ligne[1];
            ChaineDesPayeursDuCentre.CaisseDuPayeur caisse = centrePayeur == null ? null
                    : caissesParCentre.computeIfAbsent(centre, payeurs::caissesDesCentresPayeurs).get(centrePayeur);
            Cle cle = new Cle(centre, caisse == null ? "" : caisse.code());
            nombres.computeIfAbsent(cle, k -> new long[1])[0] += (Long) ligne[2];
            if (caisse != null && caisse.nom() != null) noms.merge(cle, caisse.nom(), (a, b) -> a.compareTo(b) >= 0 ? a : b);
        }
        List<CaisseRealiseePubliee> caisses = new ArrayList<>();
        nombres.forEach((cle, nombre) -> caisses.add(new CaisseRealiseePubliee(cle.centre(), cle.code(), noms.get(cle), nombre[0])));
        return caisses;
    }
}
