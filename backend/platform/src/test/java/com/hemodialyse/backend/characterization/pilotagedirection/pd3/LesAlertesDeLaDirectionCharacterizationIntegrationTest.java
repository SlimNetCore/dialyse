package com.hemodialyse.backend.characterization.pilotagedirection.pd3;

import com.hemodialyse.backend.application.direction.DashboardDiff.Change;
import com.hemodialyse.backend.application.direction.DirectionAlertHistoryService;
import com.hemodialyse.backend.application.direction.DirectionAlertPolicy.Alert;
import com.hemodialyse.backend.application.direction.DirectionAlertPolicy.Severity;
import com.hemodialyse.backend.application.direction.DirectionRealtimeService;
import com.hemodialyse.backend.characterization.pilotagedirection.PilotageDirectionCharacterizationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PD3 : alertes de la direction du LEGACY : seuils ({@code DirectionAlertPolicy}, servis dans {@code GET /api/v1/direction/indicators}),
 * historique ({@code GET /alerts/history}, {@code DirectionAlertHistoryService}, table {@code direction_alert_history}) alimenté par le recalcul
 * temps réel ({@code DirectionRealtimeService.refresh}, diffusion STOMP {@code /topic/societe/{id}/dashboard}). Règle réelle d'identité d'une alerte
 * ouverte : (centre, code), sans notion de jour ; la valeur et le nom du centre sont figés à la première apparition. Rien n'est corrigé.
 */
class LesAlertesDeLaDirectionCharacterizationIntegrationTest extends PilotageDirectionCharacterizationSupport {

    @Autowired
    private DirectionAlertHistoryService historique;
    @Autowired
    private com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection tableau;
    @Autowired
    private com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService indicateurs;
    @Autowired
    private com.hemodialyse.backend.infrastructure.websocket.WebSocketDirectionPublisher diffuseur;
    /**
     * Instance dédiée du service temps réel, câblée sur les MÊMES beans de production (lectures, historique, diffuseur STOMP réel) : seul le
     * singleton est piloté par {@code DirectionRealtimeScheduler} (recalcul chaque seconde, balayage toutes les 10 s), qui interférerait avec les
     * étapes du scénario. Aucun mock : même construction que la production.
     */
    private DirectionRealtimeService tempsReel;

    @org.junit.jupiter.api.BeforeEach
    void construireLeTempsReel() {
        tempsReel = new DirectionRealtimeService(jdbc, tableau, indicateurs, diffuseur, historique);
    }

    @Override
    protected String slice() {
        return "pd3";
    }

    @Override
    protected String dossier() {
        return "alertes";
    }

    private static Alert alerte(UUID centre, String nom, String code, Severity s, String valeur) {
        return new Alert(centre, nom, code, s, valeur == null ? null : new BigDecimal(valeur));
    }

    /** Lignes de l'historique en base, triées pour la lecture (centre, code, résolue ?, valeur) ; horodatages seulement masqués. */
    private List<String> lignesEnBase(UUID societe) {
        List<String> l = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT center_id, centre_nom, code, severity, valeur, resolved_at FROM direction_alert_history "
                + "WHERE societe_id = ? ORDER BY center_id, code, resolved_at NULLS FIRST, valeur", societe)) {
            l.add(aliasNommes.getOrDefault(String.valueOf(row.get("CENTER_ID")), String.valueOf(row.get("CENTER_ID"))) + " | " + row.get("CENTRE_NOM") + " | "
                    + row.get("CODE") + " | " + row.get("SEVERITY") + " | valeur=" + row.get("VALEUR") + " | " + (row.get("RESOLVED_AT") == null ? "OUVERTE" : "RESOLUE"));
        }
        return l;
    }

    private List<String> entrees(Resp r, Map<String, String> libelles) {
        List<String> l = new ArrayList<>();
        for (JsonNode e : corps(r)) {
            l.add(e.path("centre").asString() + " | " + e.path("code").asString() + " | " + e.path("severity").asString() + " | valeur="
                    + (e.path("valeur").isNull() ? "null" : e.path("valeur").asString()) + " | vue " + libelle(e.path("firstSeenAt"), libelles)
                    + " | resolue " + (e.path("resolvedAt").isNull() ? "-" : libelle(e.path("resolvedAt"), libelles)));
        }
        return l;
    }

    private static String libelle(JsonNode horodatage, Map<String, String> libelles) {
        if (libelles == null) return "<HORODATAGE>";
        return libelles.getOrDefault(OffsetDateTime.parse(horodatage.asString()).toInstant().toString(), "?");
    }

    @Test
    void l_historique_est_servi_du_plus_recemment_actif_au_plus_ancien_et_sa_taille_est_bornee() throws Exception {
        Map<String, String> libelles = new LinkedHashMap<>();
        OffsetDateTime t0 = CREATION_FIXE;
        for (int h : List.of(0, 1, 2, 3, 4, 5, 10)) libelles.put(t0.plusHours(h).toInstant().toString(), "T+" + h + "h");
        alerteEnBase(SOCIETE, CENTRE_A, "Centre A", "STOCK_SOUS_SEUIL", "CRITICAL", "3", t0.plusHours(1), null);
        alerteEnBase(SOCIETE, CENTRE_A, "Centre A", "LOTS_PERIMES", "CRITICAL", "2", t0.plusHours(2), t0.plusHours(3));
        alerteEnBase(SOCIETE, CENTRE_B, "Centre B", "HB_HORS_CIBLE", "WARNING", "40.0", t0.plusHours(5), null);
        alerteEnBase(SOCIETE, CENTRE_A, "Centre A", "KTV_CONFORMITE_BASSE", "WARNING", "60.0", t0, t0.plusHours(10));
        alerteEnBase(SOCIETE, CENTRE_B, "Centre B", "OBSERVANCE_EN_RETARD", "WARNING", null, t0.plusHours(4), null);
        alerteEnBase(AUTRE_SOCIETE, CENTRE_AUTRE, "Centre Confidentiel", "LOTS_PERIMES", "CRITICAL", "9", t0.plusHours(5), null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 sans parametre : ordre COALESCE(resolved_at, first_seen_at) DESC, puis first_seen_at DESC ; l'autre societe absente",
                entrees(direction("/alerts/history"), libelles));
        m.put("02 limit=2", entrees(direction("/alerts/history?limit=2"), libelles));
        m.put("03 limit=0 : borne a 1", entrees(direction("/alerts/history?limit=0"), libelles).size());
        m.put("04 limit=-5 : borne a 1", entrees(direction("/alerts/history?limit=-5"), libelles).size());
        m.put("05 limit=500 : borne a 200 (5 lignes servies)", entrees(direction("/alerts/history?limit=500"), libelles).size());
        m.put("06 limit=abc", refus(direction("/alerts/history?limit=abc")));
        m.put("07 forme d'une entree", corps(direction("/alerts/history?limit=1")).get(0));
        m.put("08 autre societe : ses alertes seules", entrees(doGet(DIRECTION + "/alerts/history", directionAutre()), libelles));
        arbrePd("historique", m);
    }

    @Test
    void une_alerte_ouverte_est_identifiee_par_centre_et_code_sans_regle_par_jour_et_garde_sa_premiere_valeur() {
        alerteEnBase(AUTRE_SOCIETE, CENTRE_AUTRE, "Centre Confidentiel", "STOCK_SOUS_SEUIL", "CRITICAL", "1", CREATION_FIXE, null);
        Map<String, Object> m = new LinkedHashMap<>();
        historique.record(SOCIETE, List.of(alerte(CENTRE_A, "Centre A", "STOCK_SOUS_SEUIL", Severity.CRITICAL, "3")));
        m.put("01 premier constat : une ligne ouverte", lignesEnBase(SOCIETE));
        historique.record(SOCIETE, List.of(alerte(CENTRE_A, "Centre A renomme", "STOCK_SOUS_SEUIL", Severity.CRITICAL, "7")));
        m.put("02 meme centre et code, valeur 7 et nom change : aucune ligne nouvelle, valeur et nom FIGES a 3 / Centre A", lignesEnBase(SOCIETE));
        historique.record(SOCIETE, List.of(alerte(CENTRE_A, "Centre A", "STOCK_SOUS_SEUIL", Severity.CRITICAL, "7"),
                alerte(CENTRE_A, "Centre A", "LOTS_PERIMES", Severity.CRITICAL, "1"), alerte(CENTRE_B, "Centre B", "STOCK_SOUS_SEUIL", Severity.CRITICAL, "2")));
        m.put("03 deux codes du meme centre le meme jour et le meme code dans un autre centre : trois lignes ouvertes", lignesEnBase(SOCIETE));
        historique.record(SOCIETE, List.of(alerte(CENTRE_B, "Centre B", "STOCK_SOUS_SEUIL", Severity.CRITICAL, "2")));
        m.put("04 les alertes du centre A disparaissent : resolues ; B reste ouverte", lignesEnBase(SOCIETE));
        historique.record(SOCIETE, List.of());
        historique.record(SOCIETE, List.of(alerte(CENTRE_B, "Centre B", "STOCK_SOUS_SEUIL", Severity.WARNING, "2")));
        m.put("05 B disparait puis reapparait le meme jour (severite changee) : nouvelle ligne ouverte, l'ancienne reste resolue", lignesEnBase(SOCIETE));
        m.put("06 l'autre societe n'est jamais touchee", lignesEnBase(AUTRE_SOCIETE));
        arbrePd("ouverture_et_resolution", m);
    }

    @Test
    void les_seuils_des_alertes_lus_dans_les_indicateurs_kdigo_observance_et_stock_a_la_date_de_fin() throws Exception {
        for (int i = 0; i < 5; i++) {
            UUID a = patientPd(CENTRE_A, "A" + i);
            analyseEnBase(CENTRE_A, a, "A_" + i, fixe(10), "kt_v_mensuel", i < 4 ? "1.30" : "1.00", "hb_g_dl", i < 2 ? "10.8" : "9.0");
            UUID b = patientPd(CENTRE_B, "B" + i);
            analyseEnBase(CENTRE_B, b, "B_" + i, fixe(10), "kt_v_mensuel", i < 3 ? "1.30" : "1.00", "hb_g_dl", i < 3 ? "10.8" : "9.0");
            if (i == 0) {
                alerteObservance(CENTRE_A, a, "OBS_A", "RETARD_CONSTATE", false);
                alerteObservance(CENTRE_B, b, "OBS_B_RESOLUE", "RETARD_CONSTATE", true);
                alerteObservance(CENTRE_B, b, "OBS_B_AUTRE_TYPE", "SURDOSAGE", false);
            }
        }
        UUID autre = patientPd(CENTRE_AUTRE, "X1");
        analyseEnBase(CENTRE_AUTRE, autre, "X_1", fixe(10), "kt_v_mensuel", "0.50", "hb_g_dl", "5.0");
        UUID sousSeuil = article(CENTRE_A, "ART_SOUS_SEUIL", "10", "10", "2.50");
        article(CENTRE_A, "ART_AU_DESSUS", "11", "10", null);
        article(CENTRE_A, "ART_SEUIL_ZERO", "0", "0", null);
        lot(CENTRE_A, sousSeuil, "PERIME_VEILLE", fixe(99), "5");
        lot(CENTRE_A, sousSeuil, "PERIME_SANS_RESTE", fixe(90), "0");
        lot(CENTRE_A, sousSeuil, "PROCHE_LE_JOUR_MEME", fixe(100), "5");
        lot(CENTRE_A, sousSeuil, "PROCHE_A_90_JOURS", fixe(190), "5");
        lot(CENTRE_A, sousSeuil, "HORS_HORIZON_91_JOURS", fixe(191), "5");
        lot(CENTRE_A, sousSeuil, "SANS_DATE", null, "5");
        Resp r = direction("/indicators?from=" + fixe(0) + "&to=" + fixe(100));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 alertes servies (A : Kt/V 80 % = seuil inclus, pas d'alerte ; Hb 40 % ; observance ouverte ; stock a la date de fin ; "
                + "B : Kt/V 60 % ; Hb 60 % ; observance resolue ou d'un autre type ignoree)", corps(r).path("alertes"));
        Map<String, Object> stock = new LinkedHashMap<>();
        for (JsonNode c : corps(r).path("centres")) stock.put(c.path("nom").asString(), c.path("stock"));
        stock.put("totaux", corps(r).path("totaux").path("stock"));
        m.put("02 stock par centre (3 articles actifs, 1 sous le seuil, perimes et proches comptes a la date de fin de periode, valeur = stock x PMP)", stock);
        Map<String, Object> cliniques = new LinkedHashMap<>();
        for (JsonNode c : corps(r).path("centres")) {
            cliniques.put(c.path("nom").asString(), c.path("clinique").path("ktV").path("pctDansCible").asString() + " | Hb "
                    + c.path("clinique").path("hemoglobine").path("pctDansCible").asString() + " | patients en retard d'observance : "
                    + (c.path("clinique").path("patientsObservanceEnRetard").isNull() ? "masque (1 a 4)" : c.path("clinique").path("patientsObservanceEnRetard").asString()));
        }
        m.put("03 taux lus (Kt/V dans la cible, Hb dans la cible, patients en retard d'observance)", cliniques);
        arbrePd("seuils", m);
    }

    @Test
    void le_recalcul_temps_reel_journalise_les_alertes_de_stock_les_diffuse_a_la_direction_connectee_puis_les_resout() throws Exception {
        LocalDate aujourdhui = LocalDate.now();
        tempsReel.watcherAdded(SOCIETE);
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("01 premier recalcul : instantane de reference, aucun changement, aucune diffusion", changes(tempsReel.refresh(SOCIETE)) + " | diffusions : " + diffusions.size());
            UUID art = article(CENTRE_A, "ART", "1", "10", "4.00");
            lot(CENTRE_A, art, "PERIME", aujourdhui.minusDays(1), "5");
            lot(CENTRE_A, art, "PROCHE", aujourdhui.plusDays(90), "5");
            lot(CENTRE_A, art, "LOIN", aujourdhui.plusDays(91), "5");
            diffusions.clear();
            m.put("02 article sous le seuil, lot perime, lot a 90 jours : changements detectes (familles STOCK et ALERTES)", changes(tempsReel.refresh(SOCIETE)));
            attendreLesEvenements();
            m.put("03 diffusion STOMP a la societe", diffusions);
            m.put("04 historique HTTP apres le recalcul", entrees(direction("/alerts/history"), null));
            jdbc.update("DELETE FROM lots WHERE article_id = ?", art);
            jdbc.update("UPDATE articles SET stock_quantity = 100 WHERE id = ?", art);
            m.put("05 stock reconstitue : alertes retombees a 0 et resolues", changes(tempsReel.refresh(SOCIETE)));
            m.put("06 historique HTTP : les trois alertes resolues", entrees(direction("/alerts/history"), null));
            m.put("07 lignes en base", lignesEnBase(SOCIETE));
            m.put("08 societe non suivie : un signal de centre ne recalcule rien", signalSansSuivi());
            arbrePd("temps_reel", m);
        } finally {
            tempsReel.watcherRemoved(SOCIETE);
        }
    }

    private String signalSansSuivi() {
        tempsReel.watcherRemoved(SOCIETE);
        boolean suivie = tempsReel.isWatched(SOCIETE);
        int avant = jdbc.queryForObject("SELECT COUNT(*) FROM direction_alert_history WHERE societe_id = ?", Integer.class, SOCIETE);
        tempsReel.markDirtyForCentre(CENTRE_A);
        tempsReel.processDirty();
        int apres = jdbc.queryForObject("SELECT COUNT(*) FROM direction_alert_history WHERE societe_id = ?", Integer.class, SOCIETE);
        return "suivie : " + suivie + " | lignes avant/apres : " + avant + "/" + apres;
    }

    private List<String> changes(List<Change> changes) {
        List<String> l = new ArrayList<>();
        for (Change c : changes) {
            l.add(aliasNommes.getOrDefault(String.valueOf(c.centerId()), String.valueOf(c.centerId())) + " | " + c.centre() + " | " + c.family() + " | " + c.name()
                    + " : " + c.before() + " -> " + c.after());
        }
        return l;
    }
}
