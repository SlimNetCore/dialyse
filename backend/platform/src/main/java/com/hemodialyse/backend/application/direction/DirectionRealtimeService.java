package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DashboardDiff.Change;
import com.hemodialyse.backend.application.direction.DashboardDiff.Snapshot;
import com.hemodialyse.backend.application.direction.DirectionBreakdownQueryService.Breakdown;
import com.hemodialyse.backend.application.direction.DirectionBreakdownQueryService.CaisseRow;
import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.CentreStats;
import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.Overview;
import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService.CentreIndicators;
import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService.Indicators;
import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService.Marker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temps réel du tableau de bord de la direction : détecte, société par société, tout changement des indicateurs
 * affichés (patients, séances, finances, caisses, clinique, anémie, stock, alertes) et le diffuse à la direction.
 * <p>
 * La détection compare des instantanés recalculés (et non des événements applicatifs isolés) : elle couvre donc
 * toute modification, quel qu'en soit le chemin (saisie, import, transfert, tâche planifiée). Les événements
 * applicatifs ({@link #markDirty}) ne servent qu'à déclencher le recalcul immédiatement plutôt qu'au prochain
 * balayage. Seules les sociétés qui ont au moins une direction connectée sont recalculées.
 */
@Service
public class DirectionRealtimeService {

    /**
     * Nombre maximal de changements par événement (le client relit de toute façon les données).
     */
    static final int MAX_CHANGES = 60;
    private static final Logger log = LoggerFactory.getLogger(DirectionRealtimeService.class);
    private final JdbcTemplate jdbc;
    private final DirectionDashboardQueryService dashboard;
    private final DirectionIndicatorsQueryService indicators;
    private final DirectionBreakdownQueryService breakdowns;
    private final DirectionRealtimePort port;
    private final DirectionAlertHistoryService alertHistory;

    private final Map<UUID, Snapshot> last = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> watchers = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> societeByCentre = new ConcurrentHashMap<>();

    public DirectionRealtimeService(JdbcTemplate jdbc, DirectionDashboardQueryService dashboard,
                                    DirectionIndicatorsQueryService indicators,
                                    DirectionBreakdownQueryService breakdowns, DirectionRealtimePort port,
                                    DirectionAlertHistoryService alertHistory) {
        this.jdbc = jdbc;
        this.dashboard = dashboard;
        this.indicators = indicators;
        this.breakdowns = breakdowns;
        this.port = port;
        this.alertHistory = alertHistory;
    }

    // ───────────────────────────── Abonnés ─────────────────────────────

    private static void marker(Snapshot s, CentreIndicators c, String name, Marker m) {
        s.put(c.centerId(), c.nom(), DashboardDiff.CLINIQUE, name + ":dansCible", m.pctDansCible());
        s.put(c.centerId(), c.nom(), DashboardDiff.CLINIQUE, name + ":evalues", m.evalues());
    }

    public void watcherAdded(UUID societeId) {
        watchers.merge(societeId, 1, Integer::sum);
        markDirty(societeId); // établit l'instantané de référence dès la première connexion
    }

    public void watcherRemoved(UUID societeId) {
        watchers.computeIfPresent(societeId, (k, n) -> n <= 1 ? null : n - 1);
        if (!watchers.containsKey(societeId)) {
            last.remove(societeId);
        }
    }

    // ───────────────────────────── Déclenchement ─────────────────────────────

    public boolean isWatched(UUID societeId) {
        return watchers.containsKey(societeId);
    }

    /**
     * Une donnée d'un centre vient de changer : la société sera recalculée au prochain passage rapide.
     */
    public void markDirtyForCentre(UUID centerId) {
        if (watchers.isEmpty() || centerId == null) return;
        UUID societeId = societeByCentre.computeIfAbsent(centerId, id -> {
            var rows = jdbc.queryForList("SELECT societe_id FROM centers WHERE id = ?", UUID.class, id);
            return rows.isEmpty() ? null : rows.get(0);
        });
        if (societeId != null) {
            markDirty(societeId);
        }
    }

    public void markDirty(UUID societeId) {
        if (societeId != null && watchers.containsKey(societeId)) {
            dirty.add(societeId);
        }
    }

    /**
     * Recalcule les sociétés signalées (appelé toutes les secondes).
     */
    public void processDirty() {
        for (UUID societeId : List.copyOf(dirty)) {
            dirty.remove(societeId);
            refresh(societeId);
        }
    }

    /**
     * Balayage de sécurité : recalcule toutes les sociétés suivies (couvre les modifications sans événement).
     */
    public void sweep() {
        for (UUID societeId : List.copyOf(watchers.keySet())) {
            refresh(societeId);
        }
    }

    // ───────────────────────────── Instantané ─────────────────────────────

    /**
     * Recalcule l'instantané de la société et diffuse les changements depuis le précédent.
     *
     * @return les changements diffusés (vide au premier calcul ou sans changement)
     */
    public List<Change> refresh(UUID societeId) {
        try {
            Snapshot current = snapshot(societeId);
            Snapshot previous = last.put(societeId, current);
            if (previous == null) {
                return List.of();
            }
            List<Change> changes = DashboardDiff.diff(previous, current);
            if (!changes.isEmpty()) {
                List<Change> capped = changes.size() > MAX_CHANGES ? changes.subList(0, MAX_CHANGES) : changes;
                port.publish(societeId, DirectionRealtimePort.DashboardChanged.of(societeId, List.copyOf(capped)));
            }
            return changes;
        } catch (RuntimeException e) {
            log.warn("Recalcul temps réel de la société {} impossible : {}", societeId, e.getMessage());
            return List.of();
        }
    }

    Snapshot snapshot(UUID societeId) {
        Overview overview = dashboard.overview(societeId, null, null);
        Indicators ind = indicators.indicators(societeId, null, null);
        Breakdown breakdown = breakdowns.breakdown(societeId, null, null);
        alertHistory.record(societeId, ind.alertes());
        Snapshot s = new Snapshot();

        for (CentreStats c : overview.centres()) {
            s.put(c.centerId(), c.nom(), DashboardDiff.PATIENTS, "patients", c.patients());
            s.put(c.centerId(), c.nom(), DashboardDiff.PATIENTS, "patientsSousKt", c.patientsSousKt());
            s.put(c.centerId(), c.nom(), DashboardDiff.SEANCES, "seances", c.seances());
            s.put(c.centerId(), c.nom(), DashboardDiff.FINANCE, "factures", c.factures());
            s.put(c.centerId(), c.nom(), DashboardDiff.FINANCE, "caTtc", c.caTtc());
            s.put(c.centerId(), c.nom(), DashboardDiff.FINANCE, "encaisse", c.encaisse());
            s.put(c.centerId(), c.nom(), DashboardDiff.FINANCE, "resteARecouvrer", c.resteARecouvrer());
        }
        for (CentreIndicators c : ind.centres()) {
            var k = c.clinique();
            marker(s, c, "ktV", k.ktV());
            marker(s, c, "hemoglobine", k.hemoglobine());
            marker(s, c, "phosphore", k.phosphore());
            marker(s, c, "pth", k.pth());
            marker(s, c, "albumine", k.albumine());
            s.put(c.centerId(), c.nom(), DashboardDiff.CLINIQUE, "vhbPositifs", k.vhbPositifs());
            s.put(c.centerId(), c.nom(), DashboardDiff.CLINIQUE, "vhcPositifs", k.vhcPositifs());
            s.put(c.centerId(), c.nom(), DashboardDiff.CLINIQUE, "vihPositifs", k.vihPositifs());
            s.put(c.centerId(), c.nom(), DashboardDiff.CLINIQUE, "observanceEnRetard", k.patientsObservanceEnRetard());
            s.put(c.centerId(), c.nom(), DashboardDiff.CLINIQUE, "greffeListeAttente", k.greffeListeAttente());
            s.put(c.centerId(), c.nom(), DashboardDiff.CLINIQUE, "greffesPeriode", k.greffesPeriode());
            var st = c.stock();
            s.put(c.centerId(), c.nom(), DashboardDiff.STOCK, "articlesSousSeuil", st.articlesSousSeuil());
            s.put(c.centerId(), c.nom(), DashboardDiff.STOCK, "lotsPerimes", st.lotsPerimes());
            s.put(c.centerId(), c.nom(), DashboardDiff.STOCK, "lotsPeremptionProche", st.lotsPeremptionProche());
            s.put(c.centerId(), c.nom(), DashboardDiff.STOCK, "valeurStock", st.valeurStock());
        }
        ind.alertes().forEach(a -> s.put(a.centerId(), a.centre(), DashboardDiff.ALERTES, a.code(),
                a.valeur() == null ? java.math.BigDecimal.ONE : a.valeur()));

        breakdown.sexe().forEach(r -> {
            s.put(r.centerId(), r.nom(), DashboardDiff.PATIENTS, "masculin", r.masculin());
            s.put(r.centerId(), r.nom(), DashboardDiff.PATIENTS, "feminin", r.feminin());
        });
        breakdown.ages().forEach(r -> r.tranches().forEach(t ->
                s.put(r.centerId(), r.nom(), DashboardDiff.PATIENTS, "age:" + t.code(), t.count())));
        for (CaisseRow r : breakdown.caisses()) {
            String key = r.caisseCode().isEmpty() ? "?" : r.caisseCode();
            s.put(r.centerId(), r.centre(), DashboardDiff.CAISSES, "seances:" + key, r.seances());
            s.put(r.centerId(), r.centre(), DashboardDiff.CAISSES, "caHt:" + key, r.caHt());
            s.put(r.centerId(), r.centre(), DashboardDiff.CAISSES, "patients:" + key, r.patients());
        }
        breakdown.anemie().forEach(r -> {
            s.put(r.centerId(), r.nom(), DashboardDiff.ANEMIE, "administreesEpo", r.administreesEpo());
            s.put(r.centerId(), r.nom(), DashboardDiff.ANEMIE, "administreesFer", r.administreesFer());
            s.put(r.centerId(), r.nom(), DashboardDiff.ANEMIE, "nonAdministrees", r.nonAdministrees());
            s.put(r.centerId(), r.nom(), DashboardDiff.ANEMIE, "patientsEpo", r.patientsEpo());
            s.put(r.centerId(), r.nom(), DashboardDiff.ANEMIE, "patientsFer", r.patientsFer());
        });
        return s;
    }
}
