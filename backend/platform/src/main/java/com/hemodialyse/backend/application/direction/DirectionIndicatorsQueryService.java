package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionAlertPolicy.Alert;
import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.CentreInfo;
import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.SocieteInfo;
import com.hemodialyse.backend.domain.medical.kdigo.service.KdigoEvaluationPolicy;
import com.hemodialyse.backend.domain.medical.kdigo.valueobject.RegleCibleKdigo;
import com.hemodialyse.backend.domain.medical.kdigo.valueobject.StatutCible;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Indicateurs médicaux, de stock et alertes de la direction d'une société : agrégats par centre et consolidés.
 * <p>
 * Aucune donnée nominative : seules des quantités et des taux sortent de cette classe, et tout effectif de patients
 * inférieur au seuil d'anonymat ({@link AnonymityPolicy}) est masqué ({@code null}) — les taux calculés sur moins de
 * patients que le seuil le sont aussi. Les cibles cliniques viennent de {@link KdigoEvaluationPolicy} (source unique).
 * Toutes les requêtes sont bornées par les centres de la société.
 */
@Service
public class DirectionIndicatorsQueryService {

    private static final int MAX_YEARS = 5;
    private final JdbcTemplate jdbc;
    private final DirectionDashboardQueryService dashboard;
    public DirectionIndicatorsQueryService(JdbcTemplate jdbc, DirectionDashboardQueryService dashboard) {
        this.jdbc = jdbc;
        this.dashboard = dashboard;
    }

    private static Object[] prepend(List<UUID> ids, Object... first) {
        List<Object> all = new ArrayList<>(List.of(first));
        all.addAll(ids);
        return all.toArray();
    }

    private static Raw emptyRaw() {
        return new Raw();
    }

    // ───────────────────────────── Chargement ─────────────────────────────

    private static CentreIndicators build(UUID centerId, String nom, boolean actif, Raw r) {
        Clinique clinique = new Clinique(
                marker(r.values.get(Marqueur.KT_V), Marqueur.KT_V.regle),
                marker(r.values.get(Marqueur.HEMOGLOBINE), Marqueur.HEMOGLOBINE.regle),
                marker(r.values.get(Marqueur.PHOSPHORE), Marqueur.PHOSPHORE.regle),
                marker(r.values.get(Marqueur.PTH), Marqueur.PTH.regle),
                marker(r.values.get(Marqueur.ALBUMINE), Marqueur.ALBUMINE.regle),
                AnonymityPolicy.mask(r.vhb), AnonymityPolicy.mask(r.vhc), AnonymityPolicy.mask(r.vih),
                AnonymityPolicy.mask(r.observance),
                AnonymityPolicy.mask(r.greffeAttente), AnonymityPolicy.mask(r.greffeBilan), AnonymityPolicy.mask(r.greffes));
        Stock stock = new Stock(r.articlesActifs, r.articlesSousSeuil, r.lotsPerimes, r.lotsProches,
                r.valeurStock.setScale(2, RoundingMode.HALF_UP));
        return new CentreIndicators(centerId, nom, actif, clinique, stock);
    }

    /**
     * Taux de patients dans / sous / au-dessus de la cible ; tout est masqué sous le seuil d'anonymat.
     */
    static Marker marker(List<BigDecimal> values, RegleCibleKdigo regle) {
        long n = values.size();
        if (n < AnonymityPolicy.THRESHOLD) {
            return new Marker(AnonymityPolicy.mask(n), null, null, null);
        }
        long dans = 0, sous = 0, dessus = 0;
        for (BigDecimal v : values) {
            StatutCible s = KdigoEvaluationPolicy.evaluer(regle, v).statut();
            if (s == StatutCible.DANS_CIBLE) dans++;
            else if (s == StatutCible.SOUS_CIBLE) sous++;
            else if (s == StatutCible.AU_DESSUS_CIBLE) dessus++;
        }
        return new Marker(n, pct(dans, n), pct(sous, n), pct(dessus, n));
    }

    private static BigDecimal pct(long part, long total) {
        return BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }

    public Indicators indicators(UUID societeId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : LocalDate.of(end.getYear(), 1, 1);
        if (start.isAfter(end)) {
            throw new BusinessException("PERIODE_INVALIDE", "La date de début doit précéder la date de fin");
        }
        if (start.plusYears(MAX_YEARS).isBefore(end)) {
            throw new BusinessException("PERIODE_TROP_LONGUE", "La période ne peut pas dépasser " + MAX_YEARS + " ans");
        }
        SocieteInfo societe = dashboard.societe(societeId);
        List<UUID> ids = societe.centres().stream().map(CentreInfo::id).toList();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (ids.isEmpty()) {
            return new Indicators(societeId, start, end, now, AnonymityPolicy.THRESHOLD, List.of(),
                    build(null, null, true, emptyRaw()), List.of());
        }
        String in = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Date sqlFrom = Date.valueOf(start);
        Date sqlTo = Date.valueOf(end);

        Map<UUID, Raw> raw = new HashMap<>();
        for (UUID id : ids) {
            raw.put(id, emptyRaw());
        }
        for (Marqueur m : Marqueur.values()) {
            loadLatestValues(m, in, ids, sqlFrom, sqlTo, raw);
        }
        loadPositives("AG_HBS", "vhb", in, ids, sqlTo, raw);
        loadPositives("AC_VHC", "vhc", in, ids, sqlTo, raw);
        loadPositives("VIH_AC", "vih", in, ids, sqlTo, raw);
        loadObservance(in, ids, raw);
        loadGreffe(in, ids, sqlFrom, sqlTo, raw);
        loadStock(in, ids, end, raw);

        List<CentreIndicators> centres = new ArrayList<>();
        List<Alert> alertes = new ArrayList<>();
        Raw total = emptyRaw();
        for (CentreInfo c : societe.centres()) {
            Raw r = raw.get(c.id());
            CentreIndicators built = build(c.id(), c.nom(), c.actif(), r);
            centres.add(built);
            total.merge(r);
            alertes.addAll(DirectionAlertPolicy.evaluate(new DirectionAlertPolicy.Inputs(c.id(), c.nom(),
                    built.clinique().ktV().pctDansCible(), built.clinique().hemoglobine().pctDansCible(),
                    r.observance > 0, r.articlesSousSeuil, r.lotsPerimes, r.lotsProches)));
        }
        return new Indicators(societeId, start, end, now, AnonymityPolicy.THRESHOLD, centres,
                build(null, null, true, total), alertes);
    }

    /**
     * Dernière valeur non nulle de chaque patient sur la période, par centre.
     */
    private void loadLatestValues(Marqueur m, String in, List<UUID> ids, Date from, Date to, Map<UUID, Raw> raw) {
        String col = m.column;
        String sql = "SELECT r.center_id, r." + col + " AS v FROM resultats_analyses r WHERE r.center_id IN (" + in + ") "
                + "AND r." + col + " IS NOT NULL AND r.date_prelevement BETWEEN ? AND ? "
                + "AND r.date_prelevement = (SELECT MAX(r2.date_prelevement) FROM resultats_analyses r2 "
                + "WHERE r2.patient_id = r.patient_id AND r2.center_id = r.center_id AND r2." + col + " IS NOT NULL "
                + "AND r2.date_prelevement BETWEEN ? AND ?)";
        List<Object> args = new ArrayList<>(ids);
        args.addAll(List.of(from, to, from, to));
        jdbc.query(sql, rs -> {
            Raw r = raw.get(rs.getObject("center_id", UUID.class));
            if (r != null) {
                r.values.get(m).add(rs.getBigDecimal("v"));
            }
        }, args.toArray());
    }

    /**
     * Patients dont le dernier résultat du marqueur (au plus tard à la fin de la période) est positif.
     */
    private void loadPositives(String marqueur, String key, String in, List<UUID> ids, Date to, Map<UUID, Raw> raw) {
        String sql = "SELECT s.center_id, COUNT(DISTINCT s.patient_id) AS n FROM serologies_patient s "
                + "WHERE s.center_id IN (" + in + ") AND s.marqueur = ? AND s.resultat = 'POSITIF' "
                + "AND s.date_prelevement = (SELECT MAX(s2.date_prelevement) FROM serologies_patient s2 "
                + "WHERE s2.patient_id = s.patient_id AND s2.center_id = s.center_id AND s2.marqueur = s.marqueur "
                + "AND s2.date_prelevement <= ?) GROUP BY s.center_id";
        List<Object> args = new ArrayList<>(ids);
        args.addAll(List.of(marqueur, to));
        jdbc.query(sql, rs -> {
            Raw r = raw.get(rs.getObject("center_id", UUID.class));
            if (r != null) {
                switch (key) {
                    case "vhb" -> r.vhb = rs.getLong("n");
                    case "vhc" -> r.vhc = rs.getLong("n");
                    default -> r.vih = rs.getLong("n");
                }
            }
        }, args.toArray());
    }

    // ───────────────────────────── Agrégation et anonymat ─────────────────────────────

    private void loadObservance(String in, List<UUID> ids, Map<UUID, Raw> raw) {
        jdbc.query("SELECT center_id, COUNT(DISTINCT patient_id) AS n FROM alertes_observance "
                        + "WHERE center_id IN (" + in + ") AND resolved_at IS NULL AND type_alerte = 'RETARD_CONSTATE' "
                        + "GROUP BY center_id",
                rs -> {
                    Raw r = raw.get(rs.getObject("center_id", UUID.class));
                    if (r != null) r.observance = rs.getLong("n");
                }, ids.toArray());
    }

    private void loadGreffe(String in, List<UUID> ids, Date from, Date to, Map<UUID, Raw> raw) {
        jdbc.query("SELECT center_id, "
                        + "SUM(CASE WHEN statut = 'INSCRIT_LISTE_ATTENTE' THEN 1 ELSE 0 END) AS attente, "
                        + "SUM(CASE WHEN statut = 'BILAN_EN_COURS' THEN 1 ELSE 0 END) AS bilan, "
                        + "SUM(CASE WHEN date_greffe BETWEEN ? AND ? THEN 1 ELSE 0 END) AS greffes "
                        + "FROM bilans_pre_greffe WHERE center_id IN (" + in + ") GROUP BY center_id",
                rs -> {
                    Raw r = raw.get(rs.getObject("center_id", UUID.class));
                    if (r != null) {
                        r.greffeAttente = rs.getLong("attente");
                        r.greffeBilan = rs.getLong("bilan");
                        r.greffes = rs.getLong("greffes");
                    }
                }, prepend(ids, from, to));
    }

    private void loadStock(String in, List<UUID> ids, LocalDate today, Map<UUID, Raw> raw) {
        jdbc.query("SELECT center_id, COUNT(*) AS actifs, "
                        + "SUM(CASE WHEN seuil_alerte > 0 AND stock_quantity <= seuil_alerte THEN 1 ELSE 0 END) AS sous, "
                        + "COALESCE(SUM(stock_quantity * COALESCE(pmp_courant, 0)), 0) AS valeur "
                        + "FROM articles WHERE center_id IN (" + in + ") AND active = TRUE GROUP BY center_id",
                rs -> {
                    Raw r = raw.get(rs.getObject("center_id", UUID.class));
                    if (r != null) {
                        r.articlesActifs = rs.getLong("actifs");
                        r.articlesSousSeuil = rs.getLong("sous");
                        r.valeurStock = rs.getBigDecimal("valeur");
                    }
                }, ids.toArray());
        Date now = Date.valueOf(today);
        Date horizon = Date.valueOf(today.plusDays(DirectionAlertPolicy.PEREMPTION_JOURS));
        List<Object> args = new ArrayList<>(List.of(now, now, horizon));
        args.addAll(ids);
        jdbc.query("SELECT center_id, "
                        + "SUM(CASE WHEN date_peremption < ? THEN 1 ELSE 0 END) AS perimes, "
                        + "SUM(CASE WHEN date_peremption >= ? AND date_peremption <= ? THEN 1 ELSE 0 END) AS proches "
                        + "FROM lots WHERE center_id IN (" + in + ") AND quantite_restante > 0 "
                        + "AND date_peremption IS NOT NULL GROUP BY center_id",
                rs -> {
                    Raw r = raw.get(rs.getObject("center_id", UUID.class));
                    if (r != null) {
                        r.lotsPerimes = rs.getLong("perimes");
                        r.lotsProches = rs.getLong("proches");
                    }
                }, args.toArray());
    }

    /**
     * Colonnes de {@code resultats_analyses} autorisées (liste blanche : jamais de nom de colonne venant d'un client).
     */
    private enum Marqueur {
        KT_V("kt_v_mensuel", KdigoEvaluationPolicy.KT_V),
        HEMOGLOBINE("hb_g_dl", KdigoEvaluationPolicy.HEMOGLOBINE),
        PHOSPHORE("phosphore_mg_dl", KdigoEvaluationPolicy.PHOSPHORE),
        PTH("pth_pg_ml", KdigoEvaluationPolicy.PTH),
        ALBUMINE("albumine_g_dl", KdigoEvaluationPolicy.ALBUMINE);

        final String column;
        final RegleCibleKdigo regle;

        Marqueur(String column, RegleCibleKdigo regle) {
            this.column = column;
            this.regle = regle;
        }
    }

    private static final class Raw {
        final Map<Marqueur, List<BigDecimal>> values = new java.util.EnumMap<>(Marqueur.class);
        long vhb, vhc, vih, observance, greffeAttente, greffeBilan, greffes;
        long articlesActifs, articlesSousSeuil, lotsPerimes, lotsProches;
        BigDecimal valeurStock = BigDecimal.ZERO;

        Raw() {
            for (Marqueur m : Marqueur.values()) {
                values.put(m, new ArrayList<>());
            }
        }

        void merge(Raw o) {
            for (Marqueur m : Marqueur.values()) {
                values.get(m).addAll(o.values.get(m));
            }
            vhb += o.vhb;
            vhc += o.vhc;
            vih += o.vih;
            observance += o.observance;
            greffeAttente += o.greffeAttente;
            greffeBilan += o.greffeBilan;
            greffes += o.greffes;
            articlesActifs += o.articlesActifs;
            articlesSousSeuil += o.articlesSousSeuil;
            lotsPerimes += o.lotsPerimes;
            lotsProches += o.lotsProches;
            valeurStock = valeurStock.add(o.valeurStock);
        }
    }

    // ───────────────────────────── Réponses ─────────────────────────────

    /**
     * Patients évalués (masqué sous le seuil) et répartition face à la cible KDIGO (masquée sous le seuil).
     */
    public record Marker(Long evalues, BigDecimal pctDansCible, BigDecimal pctSousCible, BigDecimal pctAuDessus) {
    }

    public record Clinique(Marker ktV, Marker hemoglobine, Marker phosphore, Marker pth, Marker albumine,
                           Long vhbPositifs, Long vhcPositifs, Long vihPositifs,
                           Long patientsObservanceEnRetard,
                           Long greffeListeAttente, Long greffeBilanEnCours, Long greffesPeriode) {
    }

    public record Stock(long articlesActifs, long articlesSousSeuil, long lotsPerimes, long lotsPeremptionProche,
                        BigDecimal valeurStock) {
    }

    public record CentreIndicators(UUID centerId, String nom, boolean actif, Clinique clinique, Stock stock) {
    }

    public record Indicators(UUID societeId, LocalDate from, LocalDate to, OffsetDateTime generatedAt,
                             int seuilAnonymat, List<CentreIndicators> centres, CentreIndicators totaux,
                             List<Alert> alertes) {
    }
}
