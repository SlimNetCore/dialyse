package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionAlertPolicy.Alert;
import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.CentreInfo;
import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.SocieteInfo;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.IndicateursCliniquesPublies;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.IndicateursCliniquesPublies.IndicateursCliniques;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.IndicateursCliniquesPublies.IndicateursCliniquesDesCentres;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.IndicateursCliniquesPublies.RepartitionFaceALaCible;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Indicateurs médicaux, de stock et alertes de la direction d'une société : agrégats par centre et consolidés.
 * <p>
 * Aucune donnée nominative : seules des quantités et des taux sortent de cette classe. DM9 (ADR 0101) : les indicateurs cliniques (cibles
 * KDIGO, sérologies, observance, greffes) viennent DÉJÀ MASQUÉS du langage publié du Dossier médical ({@link IndicateursCliniquesPublies},
 * seuil 5, consolidés calculés sur les bruts : KB-MED-104 reproduit, à trancher par le DPO) ; le stock du langage publié du Stock ; la
 * plateforme garde la période, la société, le masquage des effectifs de stock ({@link AnonymityPolicy}) et les alertes. Plus aucun SQL ici.
 */
@Service
public class DirectionIndicatorsQueryService {

    private static final int MAX_YEARS = 5;
    private final DirectionDashboardQueryService dashboard;
    private final IndicateursCliniquesPublies indicateursCliniques;
    private final com.hemodialyse.backend.contexts.stock.publishedlanguage.IndicateursDuStock indicateursStock;

    public DirectionIndicatorsQueryService(DirectionDashboardQueryService dashboard, IndicateursCliniquesPublies indicateursCliniques,
                                           com.hemodialyse.backend.contexts.stock.publishedlanguage.IndicateursDuStock indicateursStock) {
        this.dashboard = dashboard;
        this.indicateursCliniques = indicateursCliniques;
        this.indicateursStock = indicateursStock;
    }

    private static Marker marker(RepartitionFaceALaCible r) {
        return new Marker(r.evalues(), r.pctDansCible(), r.pctSousCible(), r.pctAuDessus());
    }

    private static Clinique clinique(IndicateursCliniques i) {
        return new Clinique(marker(i.ktV()), marker(i.hemoglobine()), marker(i.phosphore()), marker(i.pth()), marker(i.albumine()),
                i.vhbPositifs(), i.vhcPositifs(), i.vihPositifs(), i.patientsObservanceEnRetard(),
                i.greffeListeAttente(), i.greffeBilanEnCours(), i.greffesPeriode());
    }

    private static CentreIndicators build(UUID centerId, String nom, boolean actif, IndicateursCliniques clinique, StockBrut s) {
        Stock stock = new Stock(s.articlesActifs, s.articlesSousSeuil, s.lotsPerimes, s.lotsProches,
                s.valeurStock.setScale(2, RoundingMode.HALF_UP));
        return new CentreIndicators(centerId, nom, actif, clinique(clinique), stock);
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
        IndicateursCliniquesDesCentres cliniques = indicateursCliniques.desCentres(ids, start, end);
        if (ids.isEmpty()) {
            return new Indicators(societeId, start, end, now, AnonymityPolicy.THRESHOLD, List.of(),
                    build(null, null, true, cliniques.consolides(), new StockBrut()), List.of());
        }
        Map<UUID, StockBrut> stocks = loadStock(ids, end);

        List<CentreIndicators> centres = new ArrayList<>();
        List<Alert> alertes = new ArrayList<>();
        StockBrut totalStock = new StockBrut();
        for (CentreInfo c : societe.centres()) {
            IndicateursCliniques clinique = cliniques.parCentre().get(c.id());
            StockBrut stock = stocks.get(c.id());
            CentreIndicators built = build(c.id(), c.nom(), c.actif(), clinique, stock);
            centres.add(built);
            totalStock.merge(stock);
            alertes.addAll(DirectionAlertPolicy.evaluate(new DirectionAlertPolicy.Inputs(c.id(), c.nom(),
                    built.clinique().ktV().pctDansCible(), built.clinique().hemoglobine().pctDansCible(),
                    clinique.observanceEnRetard(), stock.articlesSousSeuil, stock.lotsPerimes, stock.lotsProches)));
        }
        return new Indicators(societeId, start, end, now, AnonymityPolicy.THRESHOLD, centres,
                build(null, null, true, cliniques.consolides(), totalStock), alertes);
    }

    private Map<UUID, StockBrut> loadStock(List<UUID> ids, LocalDate today) {
        var indicateurs = indicateursStock.pourCentres(ids, today);
        Map<UUID, StockBrut> stocks = new java.util.HashMap<>();
        for (UUID id : ids) {
            StockBrut s = new StockBrut();
            com.hemodialyse.backend.contexts.stock.publishedlanguage.IndicateursDuStock.Indicateur ind = indicateurs.get(id);
            if (ind != null) {
                s.articlesActifs = ind.articlesActifs();
                s.articlesSousSeuil = ind.articlesSousSeuil();
                s.valeurStock = ind.valeurStock();
                s.lotsPerimes = ind.lotsPerimes();
                s.lotsProches = ind.lotsProches();
            }
            stocks.put(id, s);
        }
        return stocks;
    }

    private static final class StockBrut {
        long articlesActifs, articlesSousSeuil, lotsPerimes, lotsProches;
        BigDecimal valeurStock = BigDecimal.ZERO;

        void merge(StockBrut o) {
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
