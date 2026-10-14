package com.hemodialyse.backend.characterization.migration;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Filet de caractérisation G6 temps 2 : la colonne « générateur » de la reprise des patients
 * ({@code PatientMigrator} + {@code MigrationConfig}, qui lisent les générateurs du centre par
 * {@code EquipementRepositoryPort.findByCentreIdAndType}). Point d'observation : la route réelle
 * {@code POST /api/v1/admin/migration/batches/{lot}/import/patients} (MockMvc, chaîne de sécurité réelle, JPA/JDBC réels
 * sur H2) ; on lit la réponse (compte rendu du lot) et les tables {@code patients} et {@code gmao_equipements}.
 * Les 5 tests unitaires {@code MigrationDomainServiceTest} (ports en mémoire) ne couvraient que le cas nominal.
 * <p>
 * Non-déterminisme : identifiants fixes ; ids générés des patients non relus (état relu par nom).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PatientsGenerateursMigrationCharacterizationIntegrationTest {

    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "migration");
    private static final String BASE = "/api/v1/admin/migration";

    private static final UUID CENTRE_A = UUID.fromString("99996500-0000-0000-0000-00000000000a");
    private static final UUID CENTRE_B = UUID.fromString("99996500-0000-0000-0000-00000000000b");
    private static final UUID ADMIN_A = UUID.fromString("99996500-0000-0000-0000-0000000000f1");
    private static final UUID ADMIN_B = UUID.fromString("99996500-0000-0000-0000-0000000000f2");
    private static final UUID TECH = UUID.fromString("99996500-0000-0000-0000-0000000000f3");

    private static final UUID SALLE_1 = UUID.fromString("99996500-0000-0000-0000-0000000005a1");
    private static final UUID SALLE_2 = UUID.fromString("99996500-0000-0000-0000-0000000005a2");
    private static final UUID SALLE_B = UUID.fromString("99996500-0000-0000-0000-0000000005b1");

    private static final UUID G01 = id(1);
    private static final UUID G02 = id(2);
    private static final UUID G_HS = id(3);
    private static final UUID G_MT = id(4);
    private static final UUID G_RF = id(5);
    private static final UUID G_SUPPRIME = id(6);
    private static final UUID STATION = id(7);
    private static final UUID G01_B = id(8);
    private static final UUID G_B1 = id(9);

    private static final String ENTETE = "legacyId;numeroAssurance;nom;prenom;sexe;dateNaissance;dateAdmission;salle;generateur";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private MockMvc mvc;
    private final Map<UUID, String> libelles = new LinkedHashMap<>();

    private static UUID id(int n) {
        return UUID.fromString(String.format("99996500-0000-0000-0000-0000000001%02d", n));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor qui(UUID centre, UUID utilisateur, String role) {
        return user(UserPrincipal.create(utilisateur.toString(), centre.toString(), "u-" + utilisateur, "", List.of(role), true));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminA() {
        return qui(CENTRE_A, ADMIN_A, "ADMIN");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminB() {
        return qui(CENTRE_B, ADMIN_B, "ADMIN");
    }

    // ------------------------------------------------------------------ harnais

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        libelles.clear();
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, capacite) VALUES (?, ?, 'S1', 'Salle 1', 10)", SALLE_1, CENTRE_A);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, capacite) VALUES (?, ?, 'S2', 'Salle 2', 10)", SALLE_2, CENTRE_A);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, capacite) VALUES (?, ?, 'SB', 'Salle B', 10)", SALLE_B, CENTRE_B);
        equipement(G01, "G-01", "GENERATEUR_DIALYSE", "EN_SERVICE", CENTRE_A, SALLE_1, null);
        equipement(G02, "G-02", "GENERATEUR_DIALYSE", "EN_SERVICE", CENTRE_A, null, null);
        equipement(G_HS, "G-HS", "GENERATEUR_DIALYSE", "HORS_SERVICE", CENTRE_A, SALLE_1, null);
        equipement(G_MT, "G-MT", "GENERATEUR_DIALYSE", "EN_MAINTENANCE", CENTRE_A, SALLE_1, null);
        equipement(G_RF, "G-RF", "GENERATEUR_DIALYSE", "REFORME", CENTRE_A, SALLE_1, null);
        equipement(G_SUPPRIME, "G-DEL", "GENERATEUR_DIALYSE", "EN_SERVICE", CENTRE_A, SALLE_1,
                OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        equipement(STATION, "E-01", "STATION_TRAITEMENT_EAU", "EN_SERVICE", CENTRE_A, SALLE_1, null);
        equipement(G01_B, "G-01", "GENERATEUR_DIALYSE", "EN_SERVICE", CENTRE_B, SALLE_B, null);
        equipement(G_B1, "G-B1", "GENERATEUR_DIALYSE", "EN_SERVICE", CENTRE_B, null, null);
        libelles.put(G01, "<G-01 centre A>");
        libelles.put(G02, "<G-02>");
        libelles.put(G_HS, "<G-HS>");
        libelles.put(G_MT, "<G-MT>");
        libelles.put(G_RF, "<G-RF>");
        libelles.put(G_SUPPRIME, "<G-DEL>");
        libelles.put(STATION, "<E-01 station>");
        libelles.put(G01_B, "<G-01 centre B>");
        libelles.put(G_B1, "<G-B1>");
        libelles.put(SALLE_1, "<S1>");
        libelles.put(SALLE_2, "<S2>");
        libelles.put(SALLE_B, "<SB>");
    }

    @AfterEach
    void cleanup() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM migration_id_map WHERE center_id = ?", c);
            jdbc.update("DELETE FROM migration_value_map WHERE center_id = ?", c);
            jdbc.update("DELETE FROM migration_entity_run WHERE batch_id IN (SELECT id FROM migration_batch WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM migration_batch WHERE center_id = ?", c);
            jdbc.update("DELETE FROM assure_patient WHERE center_id = ?", c);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", c);
            jdbc.update("DELETE FROM gmao_equipement_statut_historique WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM gmao_equipements WHERE centre_id = ?", c);
            jdbc.update("DELETE FROM salle WHERE center_id = ?", c);
        }
    }

    private void equipement(UUID id, String code, String type, String statut, UUID centre, UUID salle, OffsetDateTime supprimeLe) {
        jdbc.update("INSERT INTO gmao_equipements (id, code, designation, type, centre_id, statut, date_installation, "
                        + "date_creation, cree_par, salle_id, deleted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, code, "Equipement " + code, type, centre, statut, OffsetDateTime.of(2020, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 1, 1, 9, 0, 0, 0, ZoneOffset.UTC), ADMIN_A, salle, supprimeLe);
    }

    private record Resp(int status, String raw) {
    }

    private UUID ouvrirLot(org.springframework.test.web.servlet.request.RequestPostProcessor who) throws Exception {
        MockHttpServletResponse r = mvc.perform(post(BASE + "/batches").contentType(MediaType.APPLICATION_JSON)
                .content("{\"libelle\":\"Reprise G6 temps 2\"}").with(who)).andReturn().getResponse();
        assertEquals(201, r.getStatus(), r.getContentAsString(StandardCharsets.UTF_8));
        return UUID.fromString(mapper.readTree(r.getContentAsString(StandardCharsets.UTF_8)).path("id").asString());
    }

    private Resp importer(UUID lot, String csv, boolean dryRun, org.springframework.test.web.servlet.request.RequestPostProcessor who)
            throws Exception {
        MockHttpServletResponse r = mvc.perform(multipart(BASE + "/batches/" + lot + "/import/patients")
                .file(new MockMultipartFile("file", "patients.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .param("dryRun", String.valueOf(dryRun)).with(who)).andReturn().getResponse();
        return new Resp(r.getStatus(), r.getContentAsString(StandardCharsets.UTF_8));
    }

    private static String fichier(String entete, String... lignes) {
        return entete + "\n" + String.join("\n", lignes) + "\n";
    }

    private static String ligne(String legacy, String numero, String nom, String salle, String generateur) {
        return legacy + ";" + numero + ";" + nom + ";Karim;M;1962-03-15;2019-01-02;" + salle + ";" + generateur;
    }

    /** Compte rendu du lot, sans les champs d'exécution (utilisateur, instant). */
    private ObjectNode compteRendu(Resp r) {
        ObjectNode out = mapper.createObjectNode();
        out.put("http", r.status);
        JsonNode body = mapper.readTree(r.raw);
        for (String k : List.of("totalRows", "created", "updated", "missingColumns", "ignoredColumns", "errors", "warnings",
                "valid", "dryRun", "applied")) {
            out.set(k, trier(body.path(k)));
        }
        return out;
    }

    /** Les paramètres d'anomalie sortent d'une {@code Map.of} : l'ordre des clés varie d'une JVM à l'autre, on le fixe. */
    private JsonNode trier(JsonNode n) {
        if (n.isObject()) {
            ObjectNode o = mapper.createObjectNode();
            n.propertyNames().stream().sorted().forEach(k -> o.set(k, trier(n.get(k))));
            return o;
        }
        if (n.isArray()) {
            ArrayNode a = mapper.createArrayNode();
            n.forEach(x -> a.add(trier(x)));
            return a;
        }
        return n;
    }

    /** Patients du centre par nom : générateur et salle relus en base, libellés stables. */
    private ArrayNode etatDesPatients(UUID centre) {
        ArrayNode out = mapper.createArrayNode();
        jdbc.queryForList("SELECT nom, generateur_id, salle_id FROM patients WHERE center_id = ? ORDER BY nom, numero_assurance",
                centre).forEach(row -> {
            ObjectNode p = out.addObject();
            p.put("nom", String.valueOf(row.get("NOM")));
            p.put("generateur", libelle((UUID) row.get("GENERATEUR_ID")));
            p.put("salle", libelle((UUID) row.get("SALLE_ID")));
        });
        return out;
    }

    private String libelle(UUID id) {
        return id == null ? "<aucun>" : libelles.getOrDefault(id, "<UUID_INCONNU>");
    }

    private List<String> equipementsDuCentre(UUID centre) {
        return jdbc.queryForList("SELECT code, statut, salle_id, deleted_at IS NOT NULL AS supprime FROM gmao_equipements "
                        + "WHERE centre_id = ? ORDER BY code", centre).stream()
                .map(m -> m.get("CODE") + "/" + m.get("STATUT") + "/" + libelle((UUID) m.get("SALLE_ID")) + "/" + m.get("SUPPRIME"))
                .toList();
    }

    private void golden(String name, ObjectNode node) {
        GoldenMaster.verify(GOLDENS, name, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
    }

    // ------------------------------------------------------------------ scénarios

    @Test
    void un_generateur_du_centre_est_retrouve_par_son_code_et_donne_sa_salle_au_patient_sans_salle() throws Exception {
        UUID lot = ouvrirLot(adminA());
        List<String> avant = equipementsDuCentre(CENTRE_A);
        String csv = fichier(ENTETE,
                ligne("P1", "A-1", "SANS-SALLE", "", "G-01"),
                ligne("P2", "A-2", "GENERATEUR-SANS-SALLE", "S2", "G-02"),
                ligne("P3", "A-3", "AUTRE-SALLE", "S2", "G-01"),
                ligne("P4", "A-4", "SANS-GENERATEUR", "S1", ""),
                ligne("P5", "A-5", "NI-SALLE-NI-GENERATEUR", "", ""),
                ligne("P6", "A-6", "GENERATEUR-SANS-SALLE-NI-SALLE", "", "G-02"));

        ObjectNode racine = mapper.createObjectNode();
        ObjectNode verification = compteRendu(importer(lot, csv, true, adminA()));
        racine.set("verification", verification);
        racine.set("patients_apres_verification", etatDesPatients(CENTRE_A));
        racine.set("import", compteRendu(importer(lot, csv, false, adminA())));
        racine.set("patients_apres_import", etatDesPatients(CENTRE_A));

        assertEquals(avant, equipementsDuCentre(CENTRE_A), "la reprise ne modifie jamais les équipements");
        golden("patients_generateur_resolution_et_salle", racine);
    }

    @Test
    void la_recherche_du_generateur_ignore_casse_accents_et_ponctuation_mais_pas_les_separateurs_supprimes() throws Exception {
        UUID lot = ouvrirLot(adminA());
        ObjectNode racine = mapper.createObjectNode();
        racine.set("variantes_reconnues", compteRendu(importer(lot, fichier(ENTETE,
                ligne("P1", "B-1", "MINUSCULES", "", "g-01"),
                ligne("P2", "B-2", "ESPACE", "", "G 01"),
                ligne("P3", "B-3", "POINT", "", "g.01"),
                ligne("P4", "B-4", "ENTOURE-D-ESPACES", "", "  G-01  "),
                ligne("P5", "B-5", "TIRETS-MULTIPLES", "", "G--02")), false, adminA())));
        racine.set("patients", etatDesPatients(CENTRE_A));
        racine.set("variante_refusee_sans_separateur", compteRendu(importer(lot, fichier(ENTETE,
                ligne("P6", "B-6", "SANS-SEPARATEUR", "", "G01")), true, adminA())));
        golden("patients_generateur_normalisation_du_code", racine);
    }

    @Test
    void un_generateur_inconnu_d_un_autre_centre_supprime_ou_d_un_autre_type_est_refuse_et_aucun_patient_n_est_ecrit() throws Exception {
        UUID lot = ouvrirLot(adminA());
        String csv = fichier(ENTETE,
                ligne("P1", "C-1", "VALIDE", "", "G-01"),
                ligne("P2", "C-2", "INCONNU", "", "G-99"),
                ligne("P3", "C-3", "AUTRE-CENTRE", "", "G-B1"),
                ligne("P4", "C-4", "SUPPRIME", "", "G-DEL"),
                ligne("P5", "C-5", "STATION-D-EAU", "", "E-01"));

        ObjectNode racine = mapper.createObjectNode();
        racine.set("import", compteRendu(importer(lot, csv, false, adminA())));
        racine.set("patients", etatDesPatients(CENTRE_A));
        golden("patients_generateur_refus_et_tout_ou_rien", racine);
    }

    @Test
    void un_generateur_hors_service_en_maintenance_ou_reforme_est_accepte_sans_avertissement() throws Exception {
        UUID lot = ouvrirLot(adminA());
        ObjectNode racine = mapper.createObjectNode();
        racine.set("import", compteRendu(importer(lot, fichier(ENTETE,
                ligne("P1", "D-1", "HORS-SERVICE", "", "G-HS"),
                ligne("P2", "D-2", "EN-MAINTENANCE", "", "G-MT"),
                ligne("P3", "D-3", "REFORME", "", "G-RF")), false, adminA())));
        racine.set("patients", etatDesPatients(CENTRE_A));
        golden("patients_generateur_statut_de_l_equipement_ignore", racine);
    }

    @Test
    void chaque_centre_resout_le_code_parmi_ses_propres_generateurs() throws Exception {
        UUID lotA = ouvrirLot(adminA());
        UUID lotB = ouvrirLot(adminB());
        ObjectNode racine = mapper.createObjectNode();
        racine.set("centre_A", compteRendu(importer(lotA, fichier(ENTETE, ligne("P1", "E-1", "DU-CENTRE-A", "", "G-01")), false, adminA())));
        racine.set("centre_B", compteRendu(importer(lotB, fichier(ENTETE,
                ligne("P1", "E-1", "DU-CENTRE-B", "", "G-01"), ligne("P2", "E-2", "DU-CENTRE-B-2", "", "G-B1")), false, adminB())));
        racine.set("centre_B_refuse_un_generateur_du_centre_A", compteRendu(importer(lotB, fichier(ENTETE,
                ligne("P3", "E-3", "REFUSE", "", "G-02")), true, adminB())));
        racine.set("patients_centre_A", etatDesPatients(CENTRE_A));
        racine.set("patients_centre_B", etatDesPatients(CENTRE_B));
        golden("patients_generateur_isolement_des_centres", racine);
    }

    @Test
    void une_colonne_generateur_vide_n_efface_pas_le_generateur_d_un_patient_deja_repris_et_un_autre_code_le_remplace() throws Exception {
        UUID lot = ouvrirLot(adminA());
        ObjectNode racine = mapper.createObjectNode();
        racine.set("premier_import", compteRendu(importer(lot, fichier(ENTETE, ligne("P1", "F-1", "REPRIS", "", "G-01")), false, adminA())));
        racine.set("patients_1", etatDesPatients(CENTRE_A));
        racine.set("rejeu_colonne_vide", compteRendu(importer(lot, fichier(ENTETE, ligne("P1", "F-1", "REPRIS", "", "")), false, adminA())));
        racine.set("patients_2", etatDesPatients(CENTRE_A));
        racine.set("rejeu_autre_generateur", compteRendu(importer(lot, fichier(ENTETE, ligne("P1", "F-1", "REPRIS", "S2", "G-02")), false, adminA())));
        racine.set("patients_3", etatDesPatients(CENTRE_A));
        golden("patients_generateur_rejeu_sans_effacement", racine);
    }

    @Test
    void les_en_tetes_machine_et_numero_generateur_designent_la_colonne_generateur_sans_colonne_ignoree() throws Exception {
        UUID lot = ouvrirLot(adminA());
        ObjectNode racine = mapper.createObjectNode();
        for (String entete : List.of("Machine", "numero generateur", "Générateur (numéro)", "Générateur")) {
            String enTetes = "legacyId;numeroAssurance;nom;prenom;sexe;dateNaissance;dateAdmission;salle;" + entete;
            racine.set(entete, compteRendu(importer(lot, fichier(enTetes, ligne("P1", "G-1", "ENTETE", "", "G-01")), true, adminA())));
        }
        golden("patients_generateur_synonymes_d_en_tete", racine);
    }

    @Test
    void la_reprise_est_reservee_aux_administrateurs() throws Exception {
        UUID lot = ouvrirLot(adminA());
        Resp refus = importer(lot, fichier(ENTETE, ligne("P1", "H-1", "REFUSE", "", "G-01")), false, qui(CENTRE_A, TECH, "TECHNICIEN"));
        assertEquals(403, refus.status, refus.raw);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE center_id = ?", Integer.class, CENTRE_A));
        assertTrue(etatDesPatients(CENTRE_A).isEmpty());
    }
}
