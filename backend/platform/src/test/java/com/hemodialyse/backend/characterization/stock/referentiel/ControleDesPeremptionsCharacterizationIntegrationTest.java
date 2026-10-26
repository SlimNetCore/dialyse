package com.hemodialyse.backend.characterization.stock.referentiel;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.contexts.stock.interfaces.jobs.ControleDesPeremptionsPlanifie;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.LotProcheExpiration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Filet S8 — contrôle quotidien des péremptions, rejoué sur le nouveau code ({@code ControleDesPeremptionsPlanifie} →
 * {@code ControlerLesPeremptions}, RG-STK-060 et 061). Les goldens {@code *_nouveau_code} portent les écarts décidés le 24/10
 * (KB-STK-53) ; le comportement du legacy (journal technique seul, tous les centres, aucun dédoublonnage) est conservé dans
 * {@code _ecarts-legacy/peremptions/*.legacy.json}. Points d'observation : table {@code notification_evenement} (alerte durable),
 * événements d'intégration {@code LotProcheExpiration} reçus par un écouteur Modulith de test (registre Outbox), tables
 * {@code lots}. Horloge fixe (24/10/2026) ; dates relatives nommées {@code <J+n>} / {@code <J-n>}.
 */
class ControleDesPeremptionsCharacterizationIntegrationTest extends ReferentielStockCharacterizationSupport {

    private static final UUID SOCIETE = UUID.fromString("99997500-0000-0000-0010-000000000001");
    private static final UUID CENTRE_ACTIF = CENTRE_A;
    private static final UUID CENTRE_INACTIF = CENTRE_B;
    private static final UUID CENTRE_SANS_LOT = UUID.fromString("99997500-0000-0000-0010-0000000000c1");
    private static final List<UUID> NOS_CENTRES = List.of(CENTRE_ACTIF, CENTRE_INACTIF, CENTRE_SANS_LOT);
    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "stock", "referentiel",
            "peremptions");

    @Autowired
    private ControleDesPeremptionsPlanifie controle;
    @Autowired
    private EnregistreurDEvenements evenements;

    private LocalDate aujourdhui;

    /** Consommateur de test du contrat v1 : écouteur Modulith (livraison au moins une fois, après validation). */
    public static class EnregistreurDEvenements {
        private final List<LotProcheExpiration> recus = new CopyOnWriteArrayList<>();

        @ApplicationModuleListener
        public void sur(LotProcheExpiration evenement) {
            recus.add(evenement);
        }

        public List<LotProcheExpiration> recus() {
            return recus;
        }
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        @org.springframework.context.annotation.Primary
        com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres horlogeFixe() {
            return com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres.uniforme(
                    java.time.Clock.fixed(java.time.Instant.parse("2026-10-24T10:00:00Z"), ZoneOffset.UTC));
        }

        @Bean
        EnregistreurDEvenements enregistreurDEvenements() {
            return new EnregistreurDEvenements();
        }
    }

    @Override
    protected String theme() {
        return "peremptions";
    }

    // ------------------------------------------------------------------ harnais

    @BeforeEach
    void preparer() {
        nettoyer();
        evenements.recus().clear();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?, 'ZT-S8', 'Société S8', TRUE, CURRENT_TIMESTAMP)", SOCIETE);
        centre(CENTRE_ACTIF, "ZT-S8-A", true);
        centre(CENTRE_INACTIF, "ZT-S8-B", false);
        centre(CENTRE_SANS_LOT, "ZT-S8-C", true);
        aliasNommes.put(CENTRE_ACTIF.toString(), "<CENTRE_ACTIF>");
        aliasNommes.put(CENTRE_INACTIF.toString(), "<CENTRE_INACTIF>");
        aliasNommes.put(CENTRE_SANS_LOT.toString(), "<CENTRE_SANS_LOT>");
    }

    @AfterEach
    void nettoyer() {
        for (UUID c : NOS_CENTRES) {
            jdbc.update("DELETE FROM notification_lecture WHERE notification_id IN (SELECT id FROM notification_evenement WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM notification_evenement WHERE center_id = ?", c);
            jdbc.update("DELETE FROM lots WHERE center_id = ?", c);
            jdbc.update("DELETE FROM articles WHERE center_id = ?", c);
            jdbc.update("DELETE FROM centers WHERE id = ?", c);
        }
        jdbc.update("DELETE FROM societes WHERE id = ?", SOCIETE);
    }

    private void centre(UUID id, String code, boolean actif) {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?, ?, ?, ?, ?)", id, code, "Centre " + code, SOCIETE, actif);
    }

    private UUID art(UUID centre, String alias, boolean actif) {
        UUID id = article(centre, alias, alias, "Article " + alias, null, null, actif, null, "10", "5");
        return id;
    }

    private void lot(UUID centre, UUID article, String alias, String numero, Integer jours, String quantite) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, date_peremption, quantite_initiale, quantite_restante, pmp, "
                        + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 5, ?)", id, centre, article, numero,
                jours == null ? null : aujourdhui.plusDays(jours), new BigDecimal("100"), new BigDecimal(quantite),
                OffsetDateTime.now(ZoneOffset.UTC));
        aliasNommes.put(id.toString(), "<" + alias + ">");
    }

    /** Jour fixe du contrôle (horloge du Stock remplacée par la configuration de test) : aucune dépendance à la date réelle ni à minuit. */
    private static LocalDate jourUtc() {
        return LocalDate.of(2026, 10, 24);
    }

    /**
     * Pose les lots relativement au jour UTC courant : à rappeler après un changement de jour.
     */
    private void jeuDeLots() {
        aujourdhui = jourUtc();
        UUID a1 = art(CENTRE_ACTIF, "ART_1", true);
        UUID a2 = art(CENTRE_ACTIF, "ART_2", true);
        UUID inactif = art(CENTRE_ACTIF, "ART_INACTIF", false);
        lot(CENTRE_ACTIF, a1, "LOT_PERIME_DEPUIS_8_JOURS", "L-PERIME-8", -8, "2.5");
        lot(CENTRE_ACTIF, a1, "LOT_PERIME_DEPUIS_3_JOURS", "L-PERIME-3", -3, "2");
        lot(CENTRE_ACTIF, a1, "LOT_PERIME_HIER", "L-PERIME-1", -1, "1");
        lot(CENTRE_ACTIF, a1, "LOT_AUJOURDHUI", "L-JOUR", 0, "1");
        lot(CENTRE_ACTIF, a2, "LOT_J10_B", "L-B", 10, "4");
        lot(CENTRE_ACTIF, a2, "LOT_J10_A", "L-A", 10, "3.5");
        lot(CENTRE_ACTIF, a1, "LOT_J29", "L-29", 29, "6");
        lot(CENTRE_ACTIF, a1, "LOT_J30", "L-30", 30, "7");
        lot(CENTRE_ACTIF, a1, "LOT_J31", "L-31", 31, "8");
        lot(CENTRE_ACTIF, a2, "LOT_J90", "L-90", 90, "9");
        lot(CENTRE_ACTIF, a2, "LOT_EPUISE", "L-VIDE", 5, "0");
        lot(CENTRE_ACTIF, a2, "LOT_NEGATIF", "L-NEG", 6, "-1");
        lot(CENTRE_ACTIF, a2, "LOT_SANS_DATE", "L-SANS-DATE", null, "5");
        lot(CENTRE_ACTIF, inactif, "LOT_ARTICLE_INACTIF", "L-ART-INACTIF", 12, "2");
        UUID b1 = art(CENTRE_INACTIF, "ART_B1", true);
        lot(CENTRE_INACTIF, b1, "LOT_CENTRE_INACTIF", "L-INACTIF", 5, "11");
        lot(CENTRE_INACTIF, b1, "LOT_CENTRE_INACTIF_LOIN", "L-INACTIF-LOIN", 60, "12");
        art(CENTRE_SANS_LOT, "ART_C1", true);
    }

    /**
     * Exécute le contrôle ; rejoue si le jour UTC a changé pendant l'appel (les dates relatives seraient fausses).
     */
    private void controler() {
        for (int essai = 0; essai < 3; essai++) {
            if (jourUtc().equals(aujourdhui)) {
                controle.controlerLesPeremptions();
                if (jourUtc().equals(aujourdhui)) {
                    return;
                }
            }
            nettoyer();
            preparer();
            jeuDeLots();
        }
        throw new IllegalStateException("Le jour UTC change sans cesse");
    }

    private String normaliser(String brut) {
        String r = brut;
        for (Map.Entry<String, String> e : aliasNommes.entrySet()) {
            r = r.replace(e.getKey(), e.getValue());
        }
        for (int n = -40; n <= 120; n++) {
            r = r.replace(aujourdhui.plusDays(n).toString(), n >= 0 ? "<J+" + n + ">" : "<J" + n + ">");
        }
        return r;
    }

    private List<Map<String, Object>> alertes(UUID centre) {
        return jdbc.queryForList("SELECT id, type, payload, target_roles FROM notification_evenement WHERE center_id = ? "
                + "ORDER BY cree_le, id", centre);
    }

    private ObjectNode alerte(Map<String, Object> ligne) {
        ObjectNode a = mapper.createObjectNode();
        a.put("type", (String) ligne.get("TYPE"));
        a.put("rolesCibles", (String) ligne.get("TARGET_ROLES"));
        a.put("identifiantDeterministe", ligne.get("ID").toString().equals(UUID.nameUUIDFromBytes(
                ("LOTS_PROCHES_PEREMPTION|" + CENTRE_ACTIF + "|" + aujourdhui).getBytes(StandardCharsets.UTF_8)).toString()));
        JsonNode payload = mapper.readTree((String) ligne.get("PAYLOAD"));
        ObjectNode trie = mapper.createObjectNode();
        new TreeMap<String, JsonNode>(payload.properties().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .forEach((k, v) -> trie.put(k, normaliser(v.asString())));
        a.set("payload", trie);
        return a;
    }

    private ObjectNode alertesParCentre() {
        ObjectNode racine = mapper.createObjectNode();
        for (UUID c : NOS_CENTRES) {
            ArrayNode a = racine.putArray(normaliser(c.toString()));
            alertes(c).forEach(l -> a.add(alerte(l)));
        }
        return racine;
    }

    private List<LotProcheExpiration> attendreLesEvenements(int attendus) throws InterruptedException {
        long limite = System.currentTimeMillis() + 10_000;
        while (evenements.recus().size() < attendus && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }
        Thread.sleep(200);
        return evenements.recus().stream()
                .sorted(Comparator.comparing(LotProcheExpiration::datePeremption).thenComparing(LotProcheExpiration::numeroLot)).toList();
    }

    private void golden(String nom, ObjectNode contenu) {
        GoldenMaster.verify(GOLDENS, nom, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(contenu));
    }

    private int compter(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    // ------------------------------------------------------------------ scénarios

    @Test
    void les_lots_a_signaler_donnent_une_seule_alerte_au_pharmacien_et_a_l_admin_du_centre() {
        jeuDeLots();
        controler();
        golden("alertes_par_centre_nouveau_code", alertesParCentre());
    }

    @Test
    void les_evenements_lot_proche_expiration_v1_sont_livres_au_consommateur() throws Exception {
        jeuDeLots();
        controler();
        List<LotProcheExpiration> recus = attendreLesEvenements(7);
        ArrayNode liste = mapper.createArrayNode();
        for (LotProcheExpiration e : recus) {
            ObjectNode n = liste.addObject();
            n.put("version", e.version());
            n.put("centre", normaliser(e.centre().toString()));
            n.put("jourDuControle", normaliser(e.jourDuControle().toString()));
            n.put("codeArticle", e.codeArticle());
            n.put("numeroLot", e.numeroLot());
            n.put("datePeremption", normaliser(e.datePeremption().toString()));
            n.put("quantite", e.quantite().toPlainString());
            n.put("perime", e.perime());
            n.put("identifiantDeterministe", e.identifiant().equals(LotProcheExpiration.v1(e.centre(), e.jourDuControle(),
                    e.article(), e.codeArticle(), e.lot(), e.numeroLot(), e.datePeremption(), e.quantite(), e.perime()).identifiant()));
        }
        ObjectNode r = mapper.createObjectNode();
        r.set("evenements", liste);
        golden("evenements_lot_proche_expiration_nouveau_code", r);
    }

    @Test
    void un_centre_inactif_est_ignore_et_un_centre_sans_lot_ne_produit_aucune_alerte() {
        jeuDeLots();
        controler();
        ObjectNode b = alertesParCentre();
        ObjectNode resume = mapper.createObjectNode();
        resume.put("alertesDuCentreInactif", b.path("<CENTRE_INACTIF>").size());
        resume.put("alertesDuCentreActif", b.path("<CENTRE_ACTIF>").size());
        resume.put("alertesDuCentreSansLot", b.path("<CENTRE_SANS_LOT>").size());
        golden("centres_inactif_et_sans_lot_nouveau_code", resume);
    }

    @Test
    void la_borne_est_inclusive_a_j_plus_30_les_perimes_sont_signales_le_lendemain_puis_chaque_semaine_les_articles_inactifs_exclus()
            throws Exception {
        jeuDeLots();
        controler();
        List<LotProcheExpiration> recus = attendreLesEvenements(7);
        ObjectNode bornes = mapper.createObjectNode();
        for (String lot : List.of("L-PERIME-8", "L-PERIME-3", "L-PERIME-1", "L-JOUR", "L-29", "L-30", "L-31", "L-90", "L-VIDE",
                "L-NEG", "L-SANS-DATE", "L-ART-INACTIF", "L-INACTIF")) {
            bornes.put(lot, recus.stream().anyMatch(e -> e.numeroLot().equals(lot)));
        }
        golden("bornes_et_exclusions_nouveau_code", bornes);
    }

    @Test
    void deux_executions_le_meme_jour_ne_creent_qu_une_alerte_et_republient_les_memes_evenements_idempotents() throws Exception {
        jeuDeLots();
        controler();
        attendreLesEvenements(7);
        int apresLePremier = alertes(CENTRE_ACTIF).size();
        controler();
        List<LotProcheExpiration> recus = attendreLesEvenements(14);
        ObjectNode r = mapper.createObjectNode();
        r.put("alertesApresLePremierPassage", apresLePremier);
        r.put("alertesApresLeSecondPassage", alertes(CENTRE_ACTIF).size());
        r.put("evenementsRecus", recus.size());
        r.put("identifiantsDistincts", recus.stream().map(LotProcheExpiration::identifiant).distinct().count());
        golden("deux_passages_une_seule_alerte_nouveau_code", r);
    }

    @Test
    void le_controle_ne_modifie_aucun_lot_et_cree_une_alerte_durable_non_lue() {
        jeuDeLots();
        List<Map<String, Object>> lotsAvant = jdbc.queryForList("SELECT id, quantite_restante, date_peremption FROM lots ORDER BY id");
        int notifications = compter("notification_evenement");
        int lectures = compter("notification_lecture");
        controler();
        controler();
        ObjectNode r = mapper.createObjectNode();
        r.put("lotsInchanges", lotsAvant.equals(jdbc.queryForList("SELECT id, quantite_restante, date_peremption FROM lots ORDER BY id")));
        r.put("notificationsCreees", compter("notification_evenement") - notifications);
        r.put("lecturesCreees", compter("notification_lecture") - lectures);
        golden("lecture_seule_une_alerte_nouveau_code", r);
    }

    @Test
    void le_controle_est_planifie_chaque_jour_a_sept_heures_au_fuseau_du_serveur() throws Exception {
        Scheduled s = ControleDesPeremptionsPlanifie.class.getMethod("controlerLesPeremptions").getAnnotation(Scheduled.class);
        ObjectNode r = mapper.createObjectNode();
        r.put("cron", s.cron());
        r.put("zone", s.zone());
        golden("planification_nouveau_code", r);
    }
}
