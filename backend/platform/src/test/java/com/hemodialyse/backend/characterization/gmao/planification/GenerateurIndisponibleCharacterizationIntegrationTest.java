package com.hemodialyse.backend.characterization.gmao.planification;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CauseDIndisponibilite;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementMisHorsService;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementReforme;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.TypeEquipement;
import com.hemodialyse.backend.domain.referential.port.ReferentialRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filet de caractérisation G6 temps 2 : l'alerte « générateur indisponible » de la Planification
 * ({@code GenerateurIndisponibleService}, règle KB-GMAO-37) observée à son point d'entrée de production, l'écouteur
 * {@code GmaoEvenementsPlanificationListener} : on publie les événements du module {@code contexts/gmao}
 * ({@code EquipementMisHorsService}, {@code EquipementReforme}) dans une transaction, le registre Spring Modulith les livre
 * après commit, et l'on observe la table durable {@code notification_evenement}, le cache {@code ref.generateurs} et la
 * table {@code patients}. Aucune route HTTP, aucun mock, aucun seam.
 * <p>
 * Complète G3 (54 scénarios HTTP : alertes par route et par intervention, 5 noms max, centre) et G4 (clôture en
 * {@code A_REFORMER}) : ici le contrat du consommateur lui-même, toutes combinaisons de statuts et de types, indépendant de
 * la façon dont l'événement a été produit, et la livraison « au moins une fois » (rejeu).
 * <p>
 * Non-déterminisme : identifiants fixes ; instants et identifiants d'alerte non relus.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class GenerateurIndisponibleCharacterizationIntegrationTest {

    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "gmao", "planification");

    private static final UUID CENTRE_A = UUID.fromString("99996400-0000-0000-0000-00000000000a");
    private static final UUID CENTRE_B = UUID.fromString("99996400-0000-0000-0000-00000000000b");
    private static final UUID GENERATEUR = UUID.fromString("99996400-0000-0000-0000-000000000101");
    private static final UUID UTILISATEUR = UUID.fromString("99996400-0000-0000-0000-0000000000f1");
    private static final Instant SURVENU = Instant.parse("2026-10-01T08:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationEventPublisher evenements;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private CacheManager caches;
    @Autowired
    private ReferentialRepositoryPort referentiel;

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM notification_evenement WHERE center_id = ?", c);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", c);
        }
    }

    // ------------------------------------------------------------------ harnais

    private void attendreLaLivraison() {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(10)).until(
                () -> jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Integer.class) == 0);
    }

    private void publier(Object evenement) {
        transaction.executeWithoutResult(statut -> evenements.publishEvent(evenement));
        attendreLaLivraison();
    }

    private static EquipementMisHorsService misHorsService(UUID centre, TypeEquipement type, String code,
                                                           StatutEquipement precedent, StatutEquipement nouveau,
                                                           CauseDIndisponibilite cause) {
        return new EquipementMisHorsService(new EquipementId(GENERATEUR), new CentreId(centre), type, new CodeEquipement(code),
                precedent, nouveau, cause, new UtilisateurId(UTILISATEUR), SURVENU);
    }

    private static EquipementReforme reforme(UUID centre, TypeEquipement type, String code, StatutEquipement precedent) {
        return new EquipementReforme(new EquipementId(GENERATEUR), new CentreId(centre), type, new CodeEquipement(code),
                precedent, new UtilisateurId(UTILISATEUR), SURVENU);
    }

    private static EquipementMisHorsService generateurHorsService(UUID centre) {
        return misHorsService(centre, TypeEquipement.GENERATEUR_DIALYSE, "G-01", StatutEquipement.EN_SERVICE,
                StatutEquipement.HORS_SERVICE, CauseDIndisponibilite.MISE_HORS_SERVICE);
    }

    /** Alertes « générateur indisponible » du centre : « générateur/statut/nbPatients/patients/rôles ». */
    private List<String> alertes(UUID centre) {
        return jdbc.queryForList("SELECT payload, target_roles FROM notification_evenement WHERE center_id = ? "
                        + "AND type = 'GENERATEUR_INDISPONIBLE' ORDER BY cree_le, id", centre).stream().map(m -> {
            JsonNode p = mapper.readTree(String.valueOf(m.get("PAYLOAD")));
            return p.path("generateur").asString() + "/" + p.path("statut").asString() + "/" + p.path("nbPatients").asString()
                    + "/" + p.path("patients").asString() + "/roles" + m.get("TARGET_ROLES");
        }).toList();
    }

    private List<String> typesDeNotifications(UUID centre) {
        return jdbc.queryForList("SELECT type FROM notification_evenement WHERE center_id = ? ORDER BY type", String.class, centre);
    }

    private void viderLesNotifications() {
        jdbc.update("DELETE FROM notification_evenement WHERE center_id = ?", CENTRE_A);
    }

    private void patient(int n, UUID centre, String nom, String prenom, UUID generateur) {
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, created_at, salle_id, position_id, generateur_id, jour_lundi) "
                        + "VALUES (?, ?, ?, ?, ?, 'M', DATE '2020-01-01', ?, 'NON_VACANCIER', CURRENT_TIMESTAMP, ?, ?, ?, TRUE)",
                UUID.fromString(String.format("99996400-0000-0000-0000-0000000002%02d", n)), centre, "G6T2-PAT-" + n, nom,
                prenom, "ASS-G6T2-" + n, UUID.randomUUID(), UUID.randomUUID(), generateur);
    }

    private void golden(String name, ObjectNode node) {
        GoldenMaster.verify(GOLDENS, name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
    }

    // ------------------------------------------------------------------ règle KB-GMAO-37 : statuts

    @Test
    void la_mise_hors_service_alerte_si_et_seulement_si_le_generateur_quitte_l_etat_en_service_toutes_combinaisons() {
        ObjectNode racine = mapper.createObjectNode();
        racine.put("evenement", "EquipementMisHorsService (GENERATEUR_DIALYSE, cause MISE_HORS_SERVICE)");
        ArrayNode lignes = racine.putArray("lignes");
        for (StatutEquipement precedent : StatutEquipement.values()) {
            for (StatutEquipement nouveau : StatutEquipement.values()) {
                viderLesNotifications();
                publier(misHorsService(CENTRE_A, TypeEquipement.GENERATEUR_DIALYSE, "G-01", precedent, nouveau,
                        CauseDIndisponibilite.MISE_HORS_SERVICE));
                List<String> alertes = alertes(CENTRE_A);
                lignes.add(precedent + ">" + nouveau + " : " + (alertes.isEmpty() ? "aucune alerte" : alertes.toString()));
            }
        }
        golden("alerte_selon_statuts_mise_hors_service", racine);
    }

    @Test
    void la_reforme_alerte_si_et_seulement_si_le_generateur_etait_en_service_et_le_statut_cite_est_reforme() {
        ObjectNode racine = mapper.createObjectNode();
        racine.put("evenement", "EquipementReforme (GENERATEUR_DIALYSE)");
        ArrayNode lignes = racine.putArray("lignes");
        for (StatutEquipement precedent : StatutEquipement.values()) {
            viderLesNotifications();
            publier(reforme(CENTRE_A, TypeEquipement.GENERATEUR_DIALYSE, "G-01", precedent));
            List<String> alertes = alertes(CENTRE_A);
            lignes.add(precedent + ">REFORME : " + (alertes.isEmpty() ? "aucune alerte" : alertes.toString()));
        }
        golden("alerte_selon_statut_precedent_reforme", racine);
    }

    @Test
    void la_cause_de_l_indisponibilite_ne_change_pas_l_alerte_de_la_planification() {
        publier(misHorsService(CENTRE_A, TypeEquipement.GENERATEUR_DIALYSE, "G-01", StatutEquipement.EN_SERVICE,
                StatutEquipement.EN_MAINTENANCE, CauseDIndisponibilite.MISE_HORS_SERVICE));
        List<String> parDecision = alertes(CENTRE_A);
        viderLesNotifications();
        publier(misHorsService(CENTRE_A, TypeEquipement.GENERATEUR_DIALYSE, "G-01", StatutEquipement.EN_SERVICE,
                StatutEquipement.EN_MAINTENANCE, CauseDIndisponibilite.INTERVENTION));

        assertEquals(List.of("G-01/EN_MAINTENANCE/0//roles,ADMIN,SECRETAIRE,"), parDecision);
        assertEquals(parDecision, alertes(CENTRE_A));
    }

    @Test
    void un_etat_constate_a_reformer_par_intervention_alerte_la_planification_et_propose_la_reforme_mais_pas_par_decision() {
        publier(misHorsService(CENTRE_A, TypeEquipement.GENERATEUR_DIALYSE, "G-01", StatutEquipement.EN_SERVICE,
                StatutEquipement.A_REFORMER, CauseDIndisponibilite.INTERVENTION));
        assertEquals(List.of("G-01/A_REFORMER/0//roles,ADMIN,SECRETAIRE,"), alertes(CENTRE_A));
        assertEquals(List.of("GENERATEUR_INDISPONIBLE", "REFORME_PROPOSEE"), typesDeNotifications(CENTRE_A));

        viderLesNotifications();
        publier(misHorsService(CENTRE_A, TypeEquipement.GENERATEUR_DIALYSE, "G-01", StatutEquipement.EN_SERVICE,
                StatutEquipement.A_REFORMER, CauseDIndisponibilite.MISE_HORS_SERVICE));
        assertEquals(List.of("GENERATEUR_INDISPONIBLE"), typesDeNotifications(CENTRE_A));
    }

    // ------------------------------------------------------------------ types d'équipement

    @Test
    void seul_le_generateur_de_dialyse_alerte_parmi_les_types_d_equipement() {
        ObjectNode racine = mapper.createObjectNode();
        racine.put("evenement", "EquipementMisHorsService EN_SERVICE>HORS_SERVICE puis EquipementReforme EN_SERVICE");
        ArrayNode lignes = racine.putArray("lignes");
        for (TypeEquipement type : TypeEquipement.values()) {
            viderLesNotifications();
            publier(misHorsService(CENTRE_A, type, "T-" + type.ordinal(), StatutEquipement.EN_SERVICE,
                    StatutEquipement.HORS_SERVICE, CauseDIndisponibilite.MISE_HORS_SERVICE));
            publier(reforme(CENTRE_A, type, "T-" + type.ordinal(), StatutEquipement.EN_SERVICE));
            lignes.add(type + " : " + alertes(CENTRE_A).size() + " alerte(s) " + alertes(CENTRE_A));
        }
        golden("alerte_selon_type_d_equipement", racine);
    }

    // ------------------------------------------------------------------ patients et centre

    @Test
    void l_alerte_cite_les_patients_du_centre_de_l_evenement_places_sur_ce_generateur_tries_par_nom_et_prenom() {
        patient(1, CENTRE_A, "BENALI", "Karim", GENERATEUR);
        patient(2, CENTRE_A, "AMRANI", "Zahra", GENERATEUR);
        patient(3, CENTRE_A, "AMRANI", "Ali", GENERATEUR);
        patient(4, CENTRE_A, "SANS-GENERATEUR", "Paul", null);
        patient(5, CENTRE_A, "AUTRE-GENERATEUR", "Paul", UUID.randomUUID());
        patient(6, CENTRE_B, "AUTRE-CENTRE", "Paul", GENERATEUR);

        publier(generateurHorsService(CENTRE_A));

        assertEquals(List.of("G-01/HORS_SERVICE/3/AMRANI Ali, AMRANI Zahra, BENALI Karim/roles,ADMIN,SECRETAIRE,"), alertes(CENTRE_A));
        assertEquals(List.of(), alertes(CENTRE_B));
    }

    @Test
    void l_alerte_est_deposee_dans_le_centre_de_l_evenement_sans_relire_l_equipement_en_base() {
        // aucune ligne dans gmao_equipements : seules les valeurs portées par l'événement comptent
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM gmao_equipements WHERE id = ?", Integer.class, GENERATEUR));
        publier(generateurHorsService(CENTRE_B));

        assertEquals(List.of("G-01/HORS_SERVICE/0//roles,ADMIN,SECRETAIRE,"), alertes(CENTRE_B));
        assertEquals(List.of(), alertes(CENTRE_A));
    }

    @Test
    void les_patients_sortis_sont_ignores_y_compris_un_deces_sans_date_d_evenement() {
        patient(1, CENTRE_A, "PRESENT", "Ali", GENERATEUR);
        patient(2, CENTRE_A, "TRANSFERE-ANCIEN", "Ali", GENERATEUR);
        jdbc.update("UPDATE patients SET etat_patient = 'TRANSFERE', date_evenement_etat = DATE '2020-06-01' WHERE code_patient = ?",
                "G6T2-PAT-2");
        patient(3, CENTRE_A, "DECEDE-SANS-DATE", "Ali", GENERATEUR);
        jdbc.update("UPDATE patients SET etat_patient = 'DECEDE' WHERE code_patient = ?", "G6T2-PAT-3");

        publier(generateurHorsService(CENTRE_A));

        assertEquals(List.of("G-01/HORS_SERVICE/1/PRESENT Ali/roles,ADMIN,SECRETAIRE,"), alertes(CENTRE_A),
                "un état de sortie sans date libère la place (FinOccupation : LocalDate.MIN)");
    }

    // ------------------------------------------------------------------ livraison au moins une fois, cache

    @Test
    void un_evenement_rejoue_alerte_autant_de_fois_qu_il_est_livre_sans_dedoublonnage_contrairement_a_la_proposition_de_reforme() {
        EquipementMisHorsService evenement = misHorsService(CENTRE_A, TypeEquipement.GENERATEUR_DIALYSE, "G-01",
                StatutEquipement.EN_SERVICE, StatutEquipement.A_REFORMER, CauseDIndisponibilite.INTERVENTION);
        publier(evenement);
        publier(evenement);
        publier(evenement);

        assertEquals(3, alertes(CENTRE_A).size(), "KB-GMAO-80 : l'alerte n'a pas d'identifiant déterministe");
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(DISTINCT id) FROM notification_evenement WHERE center_id = ? "
                + "AND type = 'GENERATEUR_INDISPONIBLE'", Integer.class, CENTRE_A));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement WHERE center_id = ? "
                + "AND type = 'REFORME_PROPOSEE'", Integer.class, CENTRE_A), "la proposition de réforme est dédoublonnée");
    }

    @Test
    void la_livraison_de_l_evenement_ne_touche_ni_au_cache_ref_generateurs_ni_a_la_table_des_equipements() {
        caches.getCache("ref.generateurs").clear();
        referentiel.findGenerateurs(new CenterId(CENTRE_A));
        assertTrue(caches.getCache("ref.generateurs").get(CENTRE_A.toString()) != null, "cache amorcé");

        publier(generateurHorsService(CENTRE_A));

        assertTrue(caches.getCache("ref.generateurs").get(CENTRE_A.toString()) != null,
                "l'éviction est synchrone, portée par le contrôleur du module, pas par l'écouteur");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM gmao_equipements WHERE centre_id = ?", Integer.class, CENTRE_A));
        assertNotEquals(0, alertes(CENTRE_A).size());
    }
}
