package com.hemodialyse.backend.characterization.stock.referentiel;

import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.stock.StockDesGroupesParLeStock;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.InventaireModifie;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.MouvementsDeStockModifies;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.VerrousDeRecalculModifies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Filet de caractérisation S8 — événements actuellement publiés par le Stock (aucun n'est encore un événement d'intégration
 * versionné : {@code ReceptionValidee}, {@code SortiePourSeancesEnregistree}, {@code RapprochementValide}, {@code PmpRecalcule},
 * {@code InventaireCloture} et {@code LotProcheExpiration} n'existent pas). Publiés : {@link MouvementsDeStockModifies},
 * {@link VerrousDeRecalculModifies}, {@link InventaireModifie} (événements Spring du langage publié), relayés en STOMP
 * ({@code /topic/center/{id}/events}) par {@code StockEvenementsListener} et {@code RelaiDesInventairesVersWebSocket}, et inscrits
 * au registre {@code event_publication} (Modulith).
 * <p>
 * Points d'observation : (1) événements Spring (écouteur ajouté au contexte), (2) messages STOMP (intercepteur du canal
 * {@code brokerChannel}), (3) lignes de {@code event_publication}, (4) cache de valorisation. Les cas d'usage sont pilotés en HTTP
 * (réception, sortie, inventaire, recalcul) ; les événements sont aussi publiés directement pour verrouiller le relais (après
 * commit, annulation, cache, valeurs nulles).
 */
class EvenementsPublieDuStockCharacterizationIntegrationTest extends ReferentielStockCharacterizationSupport {

    private static final List<Object> SPRING = new CopyOnWriteArrayList<>();
    private static final AtomicReference<Object> INSTALLE = new AtomicReference<>();
    private static final String BONS_RECEPTION = "/api/v1/stock/bons-reception";
    private static final String BONS_SORTIE = "/api/v1/stock/bons-sortie";
    private static final String INV = "/api/v1/stock/inventaires";
    private static final String DASHBOARD = "/api/v1/stock/dashboard";
    private final List<Map<String, Object>> stomp = new CopyOnWriteArrayList<>();
    @Autowired
    private PlatformTransactionManager transactions;
    private int registreAvant;
    private AbstractMessageChannel canal;
    private ChannelInterceptor intercepteur;

    @Override
    protected String theme() {
        return "evenements";
    }

    // ------------------------------------------------------------------ harnais

    @BeforeEach
    void ecouter() {
        synchronized (INSTALLE) {
            if (INSTALLE.get() != context) {
                ((ConfigurableApplicationContext) context).addApplicationListener((ApplicationListener<ApplicationEvent>) e -> {
                    if (e instanceof PayloadApplicationEvent<?> p && (p.getPayload() instanceof MouvementsDeStockModifies
                            || p.getPayload() instanceof VerrousDeRecalculModifies || p.getPayload() instanceof InventaireModifie)) {
                        SPRING.add(p.getPayload());
                    }
                });
                INSTALLE.set(context);
            }
        }
        SPRING.clear();
        stomp.clear();
        canal = context.getBean("brokerChannel", AbstractMessageChannel.class);
        intercepteur = new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
                if (destination != null && destination.contains(CENTRE_A.toString())) {
                    Object charge = message.getPayload();
                    stomp.add(Map.of("destination", destination, "message", charge instanceof byte[] octets
                            ? mapper.readTree(new String(octets, java.nio.charset.StandardCharsets.UTF_8)) : String.valueOf(charge)));
                }
                return message;
            }
        };
        canal.addInterceptor(intercepteur);
        nettoyer();
        registreAvant = jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Integer.class);
    }

    @AfterEach
    void retirer() {
        if (canal != null && intercepteur != null) {
            canal.removeInterceptor(intercepteur);
        }
        nettoyer();
    }

    private void nettoyer() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM bons_reception_lignes WHERE bon_reception_id IN (SELECT id FROM bons_reception WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM bons_reception WHERE center_id = ?", c);
            jdbc.update("DELETE FROM bons_sortie_lignes WHERE bon_sortie_id IN (SELECT id FROM bons_sortie WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM bons_sortie WHERE center_id = ?", c);
            jdbc.update("DELETE FROM inventaire_lignes WHERE inventaire_id IN (SELECT id FROM inventaires WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM inventaires WHERE center_id = ?", c);
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", c);
            jdbc.update("DELETE FROM lots WHERE center_id = ?", c);
            jdbc.update("DELETE FROM app_settings WHERE center_id = ? AND cle IN ('SEQ_BR', 'SEQ_BS', 'SEQ_INV', 'INV')", c);
        }
        jdbc.update("DELETE FROM event_publication WHERE serialized_event LIKE ?", "%" + CENTRE_A + "%");
    }

    private static OffsetDateTime minuit(int moinsN) {
        return LocalDate.now().minusDays(moinsN).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private static String date(int moinsN) {
        return LocalDate.now().minusDays(moinsN).toString();
    }

    private UUID art(String alias, String code, String stock, String pmp) {
        UUID id = article(CENTRE_A, alias, code, "Article " + code, null, null, true, null, stock, pmp);
        jdbc.update("UPDATE articles SET gere_par_lot = true WHERE id = ?", id);
        return id;
    }

    private UUID lot(UUID article, String alias, String numero, String quantite, String prix) {
        UUID lot = UUID.randomUUID();
        jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, date_peremption, quantite_initiale, quantite_restante, pmp, "
                        + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", lot, CENTRE_A, article, numero, LocalDate.now().plusDays(60),
                new BigDecimal(quantite), new BigDecimal(quantite), new BigDecimal(prix), minuit(10));
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, lot_id, mouvement_type, quantite, prix_unitaire, created_by, "
                        + "created_at, saisi_le) VALUES (?, ?, ?, ?, 'ENTREE', ?, ?, 'test', ?, ?)", UUID.randomUUID(), CENTRE_A, article, lot,
                new BigDecimal(quantite), new BigDecimal(prix), minuit(10), minuit(10).plusHours(1));
        aliasNommes.put(lot.toString(), "<" + alias + ">");
        return lot;
    }

    private Resp requete(boolean ajout, String chemin, String corps) throws Exception {
        var r = ajout ? post(java.net.URI.create(chemin)) : put(java.net.URI.create(chemin));
        if (corps != null) {
            r = r.contentType(MediaType.APPLICATION_JSON).content(corps);
        }
        return send(r.with(adminA()));
    }

    private Resp lire(String chemin) throws Exception {
        return send(get(java.net.URI.create(chemin)).with(adminA()));
    }

    // ------------------------------------------------------------------ rendu

    private ObjectNode observation(Resp http) {
        ObjectNode racine = mapper.createObjectNode();
        if (http != null) {
            racine.put("statutHttp", http.status());
        }
        ArrayNode spring = racine.putArray("evenementsSpring");
        for (Object e : SPRING) {
            ObjectNode n = mapper.createObjectNode();
            n.put("type", e.getClass().getSimpleName());
            n.set("contenu", mapper.valueToTree(e));
            spring.add(n);
        }
        ArrayNode messages = racine.putArray("messagesStomp");
        for (Map<String, Object> m : stomp) {
            ObjectNode n = mapper.createObjectNode();
            n.put("destination", String.valueOf(m.get("destination")));
            n.set("message", m.get("message") instanceof JsonNode j ? j : mapper.valueToTree(m.get("message")));
            messages.add(n);
        }
        racine.put("lignesAjoutees_au_registre_event_publication", jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Integer.class) - registreAvant);
        return racine;
    }

    private void goldenObs(String nom, Resp http) {
        goldenJson(nom, observation(http));
    }

    private void goldenJson(String nom, JsonNode o) {
        String brut = o.toString().replaceAll("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+(Z|[+-]\\d{2}:?\\d{2})?", "<HORODATAGE>");
        for (int n = -120; n <= 120; n++) {
            brut = brut.replace(LocalDate.now().minusDays(n).toString(), n >= 0 ? "<J-" + n + ">" : "<J+" + (-n) + ">");
        }
        golden(nom, new Resp(200, "application/json", brut));
    }

    private void attendre(String etat) throws InterruptedException {
        for (int i = 0; i < 150; i++) {
            if (SPRING.stream().anyMatch(e -> e instanceof VerrousDeRecalculModifies v && etat.equals(v.etat()))
                    && stomp.stream().anyMatch(m -> m.get("message").toString().contains(etat))) {
                return;
            }
            Thread.sleep(100);
        }
    }

    // ------------------------------------------------------------------ cas d'usage pilotés en HTTP

    @Test
    void la_validation_d_une_reception_publie_un_mouvement_d_entree_relaye_au_centre_et_inscrit_nulle_part_au_registre() throws Exception {
        UUID a1 = art("ART_1", "A1", "0", "0");
        UUID a2 = art("ART_2", "A2", "0", "0");
        Resp creation = requete(true, BONS_RECEPTION, json("{'centerId':'" + CENTRE_A + "','dateReception':'" + date(3) + "','userId':'pharma-1',"
                + "'lignes':[{'articleId':'" + a1 + "','quantite':10,'prixUnitaire':2.5,'numeroLot':'LOT-1','datePeremption':'" + date(-90) + "'},"
                + "{'articleId':'" + a2 + "','quantite':4,'prixUnitaire':1,'numeroLot':'LOT-2','datePeremption':'" + date(-90) + "'}]}"));
        assertEquals(200, creation.status(), creation.raw());
        goldenObs("reception_brouillon_sans_evenement", creation);
        UUID bon = UUID.fromString(json(creation).path("id").asString());
        aliasNommes.put(bon.toString(), "<BON_RECEPTION>");
        SPRING.clear();
        stomp.clear();
        Resp validation = requete(true, BONS_RECEPTION + "/" + bon + "/valider", json("{'centerId':'" + CENTRE_A + "','userId':'pharma-1'}"));
        goldenObs("reception_validee", validation);
    }

    @Test
    void une_sortie_manuelle_puis_sa_correction_publient_des_mouvements_de_sortie_et_de_correction() throws Exception {
        UUID a1 = art("ART_1", "A1", "20", "5");
        UUID l1 = lot(a1, "LOT_1", "L1", "20", "5");
        String corps = json("{'centerId':'" + CENTRE_A + "','dateSortie':'" + date(1) + "','userId':'infirmier-1','items':["
                + "{'articleId':'" + a1 + "','lotId':'" + l1 + "','quantite':3}]}");
        Resp creation = requete(true, BONS_SORTIE, corps);
        goldenObs("sortie_manuelle", creation);
        if (creation.status() == 200 || creation.status() == 201) {
            UUID bon = UUID.fromString(json(creation).path("id").asString());
            aliasNommes.put(bon.toString(), "<BON_SORTIE>");
            SPRING.clear();
            stomp.clear();
            Resp correction = requete(false, BONS_SORTIE + "/" + bon, json("{'centerId':'" + CENTRE_A + "','dateSortie':'" + date(1)
                    + "','userId':'infirmier-1','items':[{'articleId':'" + a1 + "','lotId':'" + l1 + "','quantite':2}]}"));
            goldenObs("sortie_corrigee", correction);
        }
    }

    @Test
    void l_ouverture_la_cloture_et_l_annulation_d_un_inventaire_publient_un_changement_d_etat() throws Exception {
        art("ART_1", "A1", "0", "0");
        Resp ouverture = requete(true, INV, json("{'dateInventaire':'" + date(5) + "','commentaire':'Inventaire annuel'}"));
        assertEquals(201, ouverture.status(), ouverture.raw());
        UUID inv = UUID.fromString(json(ouverture).path("id").asString());
        aliasNommes.put(inv.toString(), "<INVENTAIRE_1>");
        goldenObs("inventaire_ouvert", ouverture);
        SPRING.clear();
        stomp.clear();
        goldenObs("inventaire_cloture", requete(true, INV + "/" + inv + "/cloturer", null));
        SPRING.clear();
        stomp.clear();
        Resp deuxieme = requete(true, INV, json("{'dateInventaire':'" + date(2) + "'}"));
        assertEquals(201, deuxieme.status(), deuxieme.raw());
        UUID inv2 = UUID.fromString(json(deuxieme).path("id").asString());
        aliasNommes.put(inv2.toString(), "<INVENTAIRE_2>");
        SPRING.clear();
        stomp.clear();
        goldenObs("inventaire_annule", requete(true, INV + "/" + inv2 + "/annuler", null));
    }

    @Test
    void un_recalcul_massif_du_pmp_publie_les_verrous_demarres_puis_termines() throws Exception {
        UUID a1 = art("ART_1", "A1", "0", "0");
        UUID bon = UUID.fromString(json(requete(true, BONS_RECEPTION, json("{'centerId':'" + CENTRE_A + "','dateReception':'" + date(6)
                + "','userId':'pharma-1','lignes':[{'articleId':'" + a1 + "','quantite':10,'prixUnitaire':2,'numeroLot':'L1','datePeremption':'"
                + date(-90) + "'}]}"))).path("id").asString());
        assertEquals(200, requete(true, BONS_RECEPTION + "/" + bon + "/valider", json("{'centerId':'" + CENTRE_A + "','userId':'pharma-1'}")).status());
        SPRING.clear();
        stomp.clear();
        Resp demarrage = requete(true, DASHBOARD + "/pmp-recalc/start", json("{'centerId':'" + CENTRE_A + "','articleIds':['" + a1 + "']}"));
        aliasNommes.put(json(demarrage).path("jobId").asString(), "<JOB>");
        attendre("COMPLETED");
        // le dernier message est émis par le fil du recalcul : on laisse le temps de le publier avant d'observer
        Thread.sleep(300);
        goldenObs("recalcul_massif", demarrage);
        assertEquals(200, lire(DASHBOARD + "/pmp-recalc/locks?centerId=" + CENTRE_A).status());
    }

    // ------------------------------------------------------------------ relais des événements publiés

    private void sentinelle() {
        var cache = caches.getCache(StockDesGroupesParLeStock.CACHE);
        cache.clear();
        cache.put("sentinelle", "valeur");
    }

    private boolean sentinellePresente() {
        return caches.getCache(StockDesGroupesParLeStock.CACHE).get("sentinelle") != null;
    }

    private ObjectNode relais(String nom, Object evenement) {
        sentinelle();
        SPRING.clear();
        stomp.clear();
        context.publishEvent(evenement);
        ObjectNode o = observation(null);
        o.put("cacheDeValorisationRetenu", sentinellePresente());
        return o;
    }

    @Test
    void un_mouvement_de_stock_vide_le_cache_de_valorisation_et_est_relaye_hors_transaction_avec_une_reference_vide() {
        ObjectNode a = relais("entree", new MouvementsDeStockModifies(CENTRE_A, "ENTREE", "BR-00001", 2));
        ObjectNode b = relais("sans_reference", new MouvementsDeStockModifies(CENTRE_A, "SORTIE", null, 0));
        ObjectNode r = mapper.createObjectNode();
        r.set("avecReference", a);
        r.set("sansReference", b);
        goldenJson("relais_mouvements", r);
    }

    @Test
    void les_verrous_de_recalcul_ne_vident_le_cache_qu_a_la_fin_du_recalcul() {
        UUID x = UUID.fromString("99997500-0000-0000-0001-000000000001");
        UUID y = UUID.fromString("99997500-0000-0000-0001-000000000002");
        aliasNommes.put(x.toString(), "<ART_X>");
        aliasNommes.put(y.toString(), "<ART_Y>");
        ObjectNode r = mapper.createObjectNode();
        r.set("demarre", relais("STARTED", new VerrousDeRecalculModifies(CENTRE_A, List.of(x, y), "STARTED")));
        r.set("termine", relais("COMPLETED", new VerrousDeRecalculModifies(CENTRE_A, List.of(x, y), "COMPLETED")));
        r.set("echoue", relais("FAILED", new VerrousDeRecalculModifies(CENTRE_A, List.of(x), "FAILED")));
        r.set("sansArticle", relais("VIDE", new VerrousDeRecalculModifies(CENTRE_A, List.of(), "COMPLETED")));
        goldenJson("relais_verrous_de_recalcul", r);
    }

    @Test
    void un_changement_d_inventaire_est_relaye_aux_roles_du_module_stock_avec_des_valeurs_vides_pour_les_champs_nuls() {
        ObjectNode r = mapper.createObjectNode();
        r.set("complet", relais("c", new InventaireModifie(CENTRE_A, "CLOTURE", "INV-00001", LocalDate.now().minusDays(2), "pharma-1")));
        r.set("champsNuls", relais("n", new InventaireModifie(CENTRE_A, "OUVERT", "INV-00002", null, null)));
        goldenJson("relais_inventaire", r);
    }

    @Test
    void le_relais_attend_la_validation_de_la_transaction_et_se_tait_si_elle_est_annulee() {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        ObjectNode r = mapper.createObjectNode();

        sentinelle();
        SPRING.clear();
        stomp.clear();
        ObjectNode duranteTx = mapper.createObjectNode();
        tx.executeWithoutResult(s -> {
            context.publishEvent(new MouvementsDeStockModifies(CENTRE_A, "ENTREE", "BR-TX", 1));
            context.publishEvent(new InventaireModifie(CENTRE_A, "OUVERT", "INV-TX", null, "u"));
            duranteTx.put("messagesAvantCommit", stomp.size());
            duranteTx.put("cacheRetenuAvantCommit", sentinellePresente());
        });
        duranteTx.put("messagesApresCommit", stomp.size());
        duranteTx.put("cacheRetenuApresCommit", sentinellePresente());
        r.set("validee", duranteTx);

        sentinelle();
        SPRING.clear();
        stomp.clear();
        ObjectNode annulee = mapper.createObjectNode();
        tx.executeWithoutResult(s -> {
            context.publishEvent(new MouvementsDeStockModifies(CENTRE_A, "ENTREE", "BR-TX", 1));
            context.publishEvent(new InventaireModifie(CENTRE_A, "OUVERT", "INV-TX", null, "u"));
            s.setRollbackOnly();
        });
        annulee.put("evenementsSpringPublies", SPRING.size());
        annulee.put("messagesStomp", stomp.size());
        annulee.put("cacheRetenu", sentinellePresente());
        r.set("annulee", annulee);

        JsonNode brut = r;
        goldenJson("relais_apres_commit", brut);
    }
}
