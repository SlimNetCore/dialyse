package com.hemodialyse.backend.characterization.notifications.not1;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.domain.DureesDuJournal;
import com.hemodialyse.backend.contexts.notifications.interfaces.planifie.PurgeNocturneDesNotifications;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NOT1 : diffusion en temps reel (message STOMP capte sur le canal du courtier interne, sans reseau), lien avec le journal,
 * purge de 03:40 (declenchement direct du job). Limites : {@code Instant.now()} est lu en dur (service, adaptateur, job) : les
 * dates sont semees relativement a l'horloge et masquees ; la livraison reelle a un client WebSocket n'est pas observee (le
 * courtier simple est interne, MockMvc ne monte pas le point d'entree STOMP).
 */
class DiffusionEtPurgeCharacterizationIntegrationTest extends Not1Support {

    @Autowired
    private DiffusionEnTempsReel notifications;
    @Autowired
    private com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.NotificationsDeLaFiche fiches;
    @Autowired
    private com.hemodialyse.backend.contexts.planification.publishedlanguage.AlertesDeLOptimisation optimisation;
    @Autowired
    private com.hemodialyse.backend.contexts.planification.publishedlanguage.AlertesDuPlacement placement;
    @Autowired
    private com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDesAbsencesDInfirmiers absences;
    @Autowired
    private com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps notifier;
    @Autowired
    private PurgeNocturneDesNotifications purge;

    /**
     * Une alerte personnelle à identifiant IMPOSE par le scénario (le contrat de rejeu du journal, indépendant de l'émetteur : les ports de la
     * Planification déduisent l'identifiant de leurs paramètres, PL13c). Mêmes deux temps que l'émission : journal dans la transaction, temps
     * réel après son commit.
     */
    private void alertePersonnelleAIdentifiantImpose(UUID centre, UUID infirmier, UUID compte, UUID run, UUID id) {
        var contenu = new java.util.HashMap<String, String>();
        contenu.put("infirmierId", infirmier.toString());
        contenu.put("runId", run.toString());
        contenu.put("targetRoles", "ADMIN");
        contenu.put("targetUserId", compte.toString());
        var demande = new com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification(centre,
                "INFIRMIER_ROULEMENT_MODIFIE", contenu,
                new com.hemodialyse.backend.contexts.notifications.publishedlanguage.Audience(List.of("ADMIN"), compte.toString()), id, true);
        notifier.journaliser(demande).ifPresent(aEnvoyer -> {
            Runnable tempsReel = () -> notifications.diffuser(aEnvoyer.centre(), aEnvoyer.type(), aEnvoyer.contenu(),
                    aEnvoyer.identifiant(), aEnvoyer.emisLe());
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                tempsReel.run();
                            }
                        });
            } else {
                tempsReel.run();
            }
        });
    }
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    @Qualifier("brokerChannel")
    private AbstractSubscribableChannel brokerChannel;

    private final List<Map<String, Object>> diffusions = new CopyOnWriteArrayList<>();
    private final List<String> idsDiffuses = new CopyOnWriteArrayList<>();
    private ChannelInterceptor capteur;

    @BeforeEach
    void capter() {
        diffusions.clear();
        idsDiffuses.clear();
        capteur =new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                MessageHeaders h = message.getHeaders();
                String destination = String.valueOf(h.get(SimpMessageHeaderAccessor.DESTINATION_HEADER));
                if (destination.contains(CENTRE_A.toString()) || destination.contains(CENTRE_B.toString())) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("destination", masquer(destination));
                    Object payload = message.getPayload();
                    String json = payload instanceof byte[] b ? new String(b, StandardCharsets.UTF_8) : String.valueOf(payload);
                    try {
                        JsonNode brut = mapper.readTree(json);
                        if (brut.has("id")) idsDiffuses.add(brut.get("id").asString());
                        m.put("message", masquer(brut));
                    } catch (RuntimeException e) {
                        m.put("messageTexte", masquer(json));
                    }
                    diffusions.add(m);
                }
                return message;
            }
        };
        brokerChannel.addInterceptor(capteur);
    }

    @AfterEach
    void relacher() {
        brokerChannel.removeInterceptor(capteur);
    }

    @Test
    void une_alerte_durable_est_diffusee_sur_le_canal_du_centre_et_enregistree_au_journal() throws Exception {
        placement.generateurIndisponible(CENTRE_A, nid(40), "A-G1", "HORS_SERVICE", 1, List.of("BENALI Karim"), LocalDate.of(2026, 11, 2));
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("diffusions", new ArrayList<>(diffusions));
        var reponse = get(null, admin());
        cas.put("journal admin", vue(reponse));
        cas.put("la diffusion et le journal portent le meme identifiant",
                idsDiffuses.getFirst().equals(lire(reponse).get("items").get(0).get("id").asString()));
        golden("alerte_durable", cas);
    }

    @Test
    void un_evenement_ponctuel_est_diffuse_mais_n_est_pas_au_journal() throws Exception {
        fiches.ficheCreee(CENTRE_A, nid(50), "P-50", "Benali", "Karim");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("diffusions", new ArrayList<>(diffusions));
        cas.put("journal admin", vue(get(null, admin())));
        cas.put("evenements en base", evenements());
        golden("evenement_ponctuel", cas);
    }

    @Test
    void une_alerte_personnelle_ne_diffuse_que_sa_cible_et_garde_son_contenu_au_journal() throws Exception {
        UUID compte = UUID.fromString(USER_INF);
        alertePersonnelleAIdentifiantImpose(CENTRE_A, nid(60), compte, nid(61), nid(62));
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("diffusions", new ArrayList<>(diffusions));
        cas.put("journal du compte infirmier", vue(get(null, qui(CENTRE_A, USER_INF, "INFIRMIER"))));
        cas.put("journal d'un autre infirmier", resume(get(null, qui(CENTRE_A, USER_INF_2, "INFIRMIER"))));
        cas.put("journal admin", resume(get(null, admin())));
        golden("alerte_personnelle", cas);
    }

    @Test
    void rejouer_un_evenement_a_identifiant_deterministe_ne_duplique_ni_le_journal_ni_la_diffusion() throws Exception {
        UUID compte = UUID.fromString(USER_INF);
        alertePersonnelleAIdentifiantImpose(CENTRE_A, nid(60), compte, nid(61), nid(62));
        alertePersonnelleAIdentifiantImpose(CENTRE_A, nid(60), compte, nid(61), nid(62));
        alertePersonnelleAIdentifiantImpose(CENTRE_A, nid(60), compte, nid(61), nid(63));
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("nombre de diffusions pour 3 appels (2 identiques)", diffusions.size());
        cas.put("evenements en base", evenements());
        // ordre non verrouille ici : deux alertes creees a la meme milliseconde sont departagees par identifiant
        JsonNode liste = lire(get(null, admin()));
        List<String> ids = new ArrayList<>();
        liste.get("items").forEach(i -> ids.add(masquer(i.get("id").asString())));
        java.util.Collections.sort(ids);
        cas.put("journal admin (total, identifiants tries)", List.of(liste.get("total").asLong(), ids));
        golden("rejeu_idempotent", cas);
    }

    @Test
    void une_alerte_a_identifiant_deterministe_n_est_diffusee_qu_apres_le_commit() throws Exception {
        UUID compte = UUID.fromString(USER_INF);
        TransactionTemplate tx = new TransactionTemplate(transactions);
        Map<String, Object> cas = new LinkedHashMap<>();
        tx.executeWithoutResult(s -> {
            alertePersonnelleAIdentifiantImpose(CENTRE_A, nid(60), compte, nid(61), nid(62));
            cas.put("1 dans la transaction, avant commit : diffusions", diffusions.size());
        });
        cas.put("2 apres commit : diffusions", diffusions.size());
        cas.put("3 apres commit : evenements en base", evenements());
        diffusions.clear();
        tx.executeWithoutResult(s -> {
            alertePersonnelleAIdentifiantImpose(CENTRE_A, nid(70), compte, nid(71), nid(72));
            s.setRollbackOnly();
        });
        cas.put("4 apres annulation : diffusions", diffusions.size());
        cas.put("5 apres annulation : evenements en base (le premier seulement)", evenements());
        golden("apres_le_commit", cas);
    }

    @Test
    void l_audience_ciblee_et_le_centre_sont_dans_le_journal_et_non_dans_la_destination() throws Exception {
        optimisation.propositionASignaler(CENTRE_A, nid(80), "COUVERTURE", "SOUS_EFFECTIF", 3);
        absences.absenceEnregistree(CENTRE_B, "Sara Amrani", LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 29), true);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("diffusions", new ArrayList<>(diffusions));
        cas.put("journal admin A", resume(get(null, admin())));
        cas.put("journal medecin A", resume(get(null, qui(CENTRE_A, USER_MED, "MEDECIN"))));
        cas.put("journal medecin B", resume(get(null, qui(CENTRE_B, USER_B, "MEDECIN"))));
        golden("audience_et_centre", cas);
    }

    @Test
    void la_purge_supprime_au_dela_de_soixante_jours_avec_les_lectures_et_garde_le_reste() throws Exception {
        evenement(1, CENTRE_A, "GENERATEUR_INDISPONIBLE", "{\"cle\":\"v1\"}", "ADMIN", null, il_y_a_jours(61, 0));
        evenement(2, CENTRE_A, "GENERATEUR_INDISPONIBLE", "{\"cle\":\"v2\"}", "ADMIN", null, il_y_a_jours(59, 0));
        evenement(3, CENTRE_B, "GENERATEUR_INDISPONIBLE", "{\"cle\":\"v3\"}", "ADMIN", null, il_y_a_jours(90, 0));
        evenement(4, CENTRE_A, "GENERATEUR_INDISPONIBLE", "{\"cle\":\"v4\"}", "ADMIN", null, il_y_a_jours(31, 0));
        evenement(5, CENTRE_A, "GENERATEUR_INDISPONIBLE", "{\"cle\":\"v5\"}", "ADMIN", null, il_y_a(5));
        jdbc.update("INSERT INTO notification_lecture (notification_id, user_id, lue_le) VALUES (?, ?, CURRENT_TIMESTAMP)", nid(1), USER_ADMIN);
        jdbc.update("INSERT INTO notification_lecture (notification_id, user_id, lue_le) VALUES (?, ?, CURRENT_TIMESTAMP)", nid(2), USER_ADMIN);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("0 avant", restants());
        purge.purger();
        cas.put("1 apres la purge (N01 N03 supprimees : > 60 jours)", restants());
        purge.purger();
        cas.put("2 apres une seconde purge (inchange)", restants());
        cas.put("3 lectures restantes de l'admin (N02 seulement)", lectures(USER_ADMIN));
        golden("purge", cas);
    }

    @Test
    void la_purge_est_planifiee_chaque_jour_a_03h40_et_garde_soixante_jours() throws Exception {
        Scheduled s = PurgeNocturneDesNotifications.class.getMethod("purger").getAnnotation(Scheduled.class);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("cron", s.cron());
        cas.put("conservation en jours (champ package-private)", conservation());
        golden("planification", cas);
    }

    private long conservation() {
        return DureesDuJournal.CONSERVATION.toDays();
    }

    private List<String> restants() {
        return jdbc.queryForList("SELECT id FROM notification_evenement WHERE center_id IN (?, ?) ORDER BY id", CENTRE_A, CENTRE_B)
                .stream().map(m -> masquer(String.valueOf(m.get("ID")))).toList();
    }
}
