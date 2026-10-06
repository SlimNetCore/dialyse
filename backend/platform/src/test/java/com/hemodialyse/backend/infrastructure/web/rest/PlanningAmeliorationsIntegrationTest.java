package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.planning.optimisation.OptimisationApplicationService;
import com.hemodialyse.backend.application.planning.optimisation.OptimisationPlanningService;
import com.hemodialyse.backend.application.planning.optimisation.ReplanificationAutomatiqueService;
import com.hemodialyse.backend.domain.planning.model.DeplacementTemporaire;
import com.hemodialyse.backend.domain.planning.optimisation.model.CompetenceInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation.PatientAPlacer;
import com.hemodialyse.backend.domain.planning.optimisation.model.MotifProposition;
import com.hemodialyse.backend.domain.planning.optimisation.model.ObjectifInfirmiers;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation.StatutRun;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationDonneesPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationRunRepositoryPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.ReglagesOptimisationPort;
import com.hemodialyse.backend.domain.planning.port.DeplacementTemporairePort;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Améliorations de la planification sur la base H2 : préférences des patients, profils des infirmiers et réglages par
 * centre (REST, isolation entre centres), lecture des compétences et des maintenances GMAO, déplacements temporaires,
 * jours choisis par l'optimisation et replanification automatique nocturne.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PlanningAmeliorationsIntegrationTest {

    private static final UUID SOC = UUID.fromString("99998000-0000-0000-0000-000000000001");
    private static final UUID C1 = UUID.fromString("99998000-0000-0000-0000-0000000000c1");
    private static final UUID C2 = UUID.fromString("99998000-0000-0000-0000-0000000000c2");
    private static final LocalDate DIMANCHE = LocalDate.of(2026, 9, 27);
    private static final LocalDate LUNDI = DIMANCHE.plusDays(1);

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OptimisationDonneesPort donnees;
    @Autowired
    private OptimisationPlanningService planification;
    @Autowired
    private OptimisationApplicationService application;
    @Autowired
    private OptimisationRunRepositoryPort runs;
    @Autowired
    private ReglagesOptimisationPort reglages;
    @Autowired
    private DeplacementTemporairePort temporaires;
    @Autowired
    private ReplanificationAutomatiqueService replanification;

    private MockMvc mockMvc;
    private UUID salleA;
    private UUID matin;
    private UUID soir;
    private UUID soirAutreCentre;
    private UUID a1;
    private UUID a2;
    private UUID alpha;
    private UUID bravo;
    private UUID etranger;
    private UUID marie;

    private static RequestPostProcessor as(UUID center, String role) {
        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID().toString(), center.toString(), "u-" + role, "",
                List.of(role), true);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC, "ZT-OPT2", "Société OPT2");
        centre(C1, "ZT-OPT2-A");
        centre(C2, "ZT-OPT2-B");
        salleA = salle(C1, "ZT-PA", "Salle A");
        matin = creneau(C1, "ZT-PM", "Matin");
        soir = creneau(C1, "ZT-PS", "Soir");
        UUID salleAutre = salle(C2, "ZT-PC", "Salle autre centre");
        soirAutreCentre = creneau(C2, "ZT-PS2", "Soir autre centre");
        a1 = generateur(C1, "ZT-PA1", salleA);
        a2 = generateur(C1, "ZT-PA2", salleA);
        UUID autreGen = generateur(C2, "ZT-PC1", salleAutre);

        alpha = patient(C1, "Alpha", "2015-03-01", salleA, matin, a1, true);
        bravo = patient(C1, "Bravo", "1970-05-05", null, null, null, false);
        etranger = patient(C2, "Etranger", "1970-05-05", salleAutre, soirAutreCentre, autreGen, true);
        jdbc.update("INSERT INTO abords_vasculaires (id, actif, center_id, patient_id, type_abord) VALUES (?,TRUE,?,?,?)",
                UUID.randomUUID(), C1, alpha, "KT_TUNNELISE");
        marie = infirmier(C1, "ZT-PI1", "Marie");
        infirmier(C2, "ZT-PI2", "Etrangère");
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("planification_optimisation", "deplacement_temporaire", "planning_preference_patient",
                "infirmier_profil_planning", "planification_reglages", "abords_vasculaires", "infirmier_affectation",
                "infirmier", "patients", "position_creneau", "salle")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", C1, C2);
        }
        jdbc.update("DELETE FROM gmao_interventions WHERE centre_id IN (?, ?)", C1, C2);
        jdbc.update("DELETE FROM gmao_equipements WHERE centre_id IN (?, ?)", C1, C2);
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-OPT2%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-OPT2%'");
    }

    private RunOptimisation attendre(UUID runId) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (System.nanoTime() < limite) {
            RunOptimisation run = planification.consulter(C1, runId);
            if (!run.enCours()) return run;
            Thread.sleep(200);
        }
        throw new AssertionError("optimisation trop longue");
    }

    private ParametresOptimisation parametres(PerimetreOptimisation perimetre) {
        return new ParametresOptimisation(perimetre, DIMANCHE, 1, 2, 5, ObjectifInfirmiers.EQUITE, 2, 6);
    }

    @Test
    void should_manage_preferences_profiles_and_settings_of_the_current_center_only() throws Exception {
        mockMvc.perform(get("/api/v1/planning/preferences/patients").param("page", "0").param("size", "10")
                        .with(as(C1, "SECRETAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].patientId").value(hasItem(bravo.toString())))
                .andExpect(jsonPath("$.items[*].patientId").value(not(hasItem(etranger.toString()))));

        mockMvc.perform(put("/api/v1/planning/preferences/patients/{id}", bravo).with(as(C1, "SECRETAIRE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creneauPrefereId\":\"" + soir + "\",\"seancesParSemaine\":2,\"joursAChoisir\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seancesParSemaine").value(2));
        mockMvc.perform(put("/api/v1/planning/preferences/patients/{id}", etranger).with(as(C1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"joursAChoisir\":false}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PREFERENCE_PATIENT_INTROUVABLE"));
        mockMvc.perform(put("/api/v1/planning/preferences/patients/{id}", alpha).with(as(C1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creneauPrefereId\":\"" + soirAutreCentre + "\",\"joursAChoisir\":false}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PREFERENCE_CRENEAU_INCONNU"));

        mockMvc.perform(put("/api/v1/planning/preferences/infirmiers/{id}", marie).with(as(C1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tauxActivite\":50,\"competences\":[\"PEDIATRIE\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/planning/preferences/infirmiers").with(as(C1, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].profil.tauxActivite").value(50));
        mockMvc.perform(put("/api/v1/planning/preferences/infirmiers/{id}", UUID.randomUUID()).with(as(C1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tauxActivite\":80}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PROFIL_INFIRMIER_INTROUVABLE"));

        String reglagesJson = "{\"replanificationAuto\":true,\"heuresParVacation\":6,\"heuresHebdoTempsPlein\":35,"
                + "\"reposHebdoMin\":2}";
        mockMvc.perform(put("/api/v1/planning/preferences/reglages").with(as(C1, "SECRETAIRE"))
                        .contentType(MediaType.APPLICATION_JSON).content(reglagesJson))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/planning/preferences/reglages").with(as(C1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(reglagesJson))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/planning/preferences/reglages").with(as(C2, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replanificationAuto").value(false));
        assertThat(reglages.centresEnReplanificationAuto()).contains(C1).doesNotContain(C2);
        assertThat(reglages.lire(C1).heuresHebdoTempsPlein()).isEqualTo(35);
    }

    @Test
    void should_read_skills_preferences_profiles_and_generator_maintenance_of_the_center() throws Exception {
        mockMvc.perform(put("/api/v1/planning/preferences/patients/{id}", bravo).with(as(C1, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"creneauPrefereId\":\"" + soir + "\",\"seancesParSemaine\":2,\"joursAChoisir\":true}"));
        mockMvc.perform(put("/api/v1/planning/preferences/infirmiers/{id}", marie).with(as(C1, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"tauxActivite\":50,\"competences\":[\"CATHETER\"]}"));
        intervention(C1, a1, "PLANIFIEE", LUNDI.atTime(8, 0), null);

        DonneesOptimisation lues = donnees.charger(C1, DIMANCHE, DIMANCHE.plusDays(6));

        PatientAPlacer enfant = lues.patients().stream().filter(p -> p.patientId().equals(alpha)).findFirst().orElseThrow();
        assertThat(enfant.competencesRequises())
                .containsExactlyInAnyOrder(CompetenceInfirmier.PEDIATRIE, CompetenceInfirmier.CATHETER);
        PatientAPlacer nouveau = lues.patients().stream().filter(p -> p.patientId().equals(bravo)).findFirst()
                .orElseThrow(() -> new AssertionError("un patient aux jours à choisir est à placer, même sans jours"));
        assertThat(nouveau.seancesAChoisir()).isEqualTo(2);
        assertThat(nouveau.creneauPrefereId()).isEqualTo(soir);
        assertThat(lues.profil(marie).tauxActivite()).isEqualTo(50);
        assertThat(lues.profil(marie).competences()).containsExactly(CompetenceInfirmier.CATHETER);
        assertThat(lues.indisponibilites()).singleElement().satisfies(i -> {
            assertThat(i.generateurId()).isEqualTo(a1);
            assertThat(i.debut()).isEqualTo(LUNDI);
            assertThat(i.fin()).isEqualTo(LUNDI);
        });
        assertThat(donnees.charger(C2, DIMANCHE, DIMANCHE.plusDays(6)).indisponibilites()).isEmpty();
    }

    @Test
    void should_propose_and_record_a_temporary_move_for_a_generator_under_maintenance() throws Exception {
        intervention(C1, a1, "PLANIFIEE", LUNDI.atTime(8, 0), LUNDI.atTime(17, 0));

        RunOptimisation fini = attendre(planification.lancer(C1, parametres(PerimetreOptimisation.MAINTENANCE), "admin")
                .id());

        assertThat(fini.statut()).isEqualTo(StatutRun.TERMINEE);
        assertThat(fini.resultat().temporaires()).singleElement().satisfies(t -> {
            assertThat(t.patientId()).isEqualTo(alpha);
            assertThat(t.date()).isEqualTo(LUNDI);
            assertThat(t.vers().generateurId()).isEqualTo(a2);
        });
        assertThat(fini.resume().temporaires()).isEqualTo(1);

        application.appliquer(C1, fini.id());

        List<DeplacementTemporaire> enregistres = temporaires.entre(C1, DIMANCHE, DIMANCHE.plusDays(6));
        assertThat(enregistres).singleElement().satisfies(t -> {
            assertThat(t.patientId()).isEqualTo(alpha);
            assertThat(t.generateurId()).isEqualTo(a2);
        });
        assertThat(temporaires.entre(C2, DIMANCHE, DIMANCHE.plusDays(6))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT generateur_id FROM patients WHERE id = ?", UUID.class, alpha))
                .as("la place habituelle ne change pas").isEqualTo(a1);
    }

    @Test
    void should_choose_and_record_the_dialysis_days_of_a_new_patient() throws Exception {
        mockMvc.perform(put("/api/v1/planning/preferences/patients/{id}", bravo).with(as(C1, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"seancesParSemaine\":2,\"joursAChoisir\":true}"))
                .andExpect(status().isOk());

        RunOptimisation fini = attendre(planification.lancer(C1, parametres(PerimetreOptimisation.PATIENTS), "admin").id());

        assertThat(fini.resultat().deplacements()).filteredOn(d -> d.patientId().equals(bravo)).singleElement()
                .satisfies(d -> assertThat(d.jours()).hasSize(2));

        application.appliquer(C1, fini.id());

        Integer jours = jdbc.queryForObject("SELECT (CASE WHEN jour_dimanche THEN 1 ELSE 0 END) + (CASE WHEN jour_lundi "
                + "THEN 1 ELSE 0 END) + (CASE WHEN jour_mardi THEN 1 ELSE 0 END) + (CASE WHEN jour_mercredi THEN 1 ELSE 0 "
                + "END) + (CASE WHEN jour_jeudi THEN 1 ELSE 0 END) + (CASE WHEN jour_vendredi THEN 1 ELSE 0 END) + (CASE "
                + "WHEN jour_samedi THEN 1 ELSE 0 END) FROM patients WHERE id = ?", Integer.class, bravo);
        assertThat(jours).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT salle_id FROM patients WHERE id = ?", UUID.class, bravo)).isEqualTo(salleA);
    }

    @Test
    void should_chain_the_nightly_replanning_and_find_the_maintenance_to_organise() throws Exception {
        intervention(C1, a1, "PLANIFIEE", LUNDI.atTime(8, 0), LUNDI.atTime(17, 0));

        replanification.replanifier(C1, DIMANCHE);

        long limite = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (System.nanoTime() < limite && (runs.findPaged(C1, 0, 10).total() < 3 || runs.findEnCours(C1).isPresent())) {
            Thread.sleep(200);
        }
        assertThat(runs.findPaged(C1, 0, 10).items()).extracting(r -> r.parametres().perimetre())
                .containsExactlyInAnyOrder(PerimetreOptimisation.COUVERTURE, PerimetreOptimisation.MAINTENANCE,
                        PerimetreOptimisation.PATIENTS);
        assertThat(runs.findPaged(C1, 0, 10).items()).allSatisfy(r -> {
            assertThat(r.lancePar()).isEqualTo(ReplanificationAutomatiqueService.UTILISATEUR);
            assertThat(r.appliqueLe()).as("rien n'est appliqué d'office").isNull();
        });
        RunOptimisation maintenance = runs.findPaged(C1, 0, 10).items().stream()
                .filter(r -> r.parametres().perimetre() == PerimetreOptimisation.MAINTENANCE).findFirst().orElseThrow();
        assertThat(maintenance.statut()).isEqualTo(StatutRun.TERMINEE);
        assertThat(maintenance.parametres().nbSemaines()).isEqualTo(2);
        assertThat(MotifProposition.MAINTENANCE.valeur(planification.consulter(C1, maintenance.id()).resultat()))
                .as("la séance du lundi est à déplacer : la proposition est notifiée").isEqualTo(1);
        assertThat(runs.findPaged(C2, 0, 10).total()).isZero();
    }

    // ─────────────────────────────── données de test ───────────────────────────────

    private void centre(UUID id, String code) {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", id, code, code, SOC);
    }

    private UUID salle(UUID centre, String code, String nom) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?,?,?,?)", id, centre, code, nom);
        return id;
    }

    private UUID creneau(UUID centre, String code, String libelle) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?,?,?,?)", id, centre, code, libelle);
        return id;
    }

    private UUID generateur(UUID centre, String code, UUID salle) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO gmao_equipements (id, code, designation, type, centre_id, statut, salle_id, "
                        + "date_installation, date_creation, cree_par) VALUES (?, ?, ?, 'GENERATEUR_DIALYSE', ?, 'EN_SERVICE', ?, ?, ?, ?)",
                id, code, "Générateur " + code, centre, salle, OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC), new UUID(0, 0));
        return id;
    }

    private void intervention(UUID centre, UUID equipement, String statut, java.time.LocalDateTime debut,
                              java.time.LocalDateTime fin) {
        jdbc.update("INSERT INTO gmao_interventions (id, equipement_id, centre_id, type, statut, date_debut, date_fin, "
                        + "description, date_creation, cree_par) VALUES (?, ?, ?, 'PREVENTIVE', ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), equipement, centre, statut, Timestamp.valueOf(debut),
                fin == null ? null : Timestamp.valueOf(fin), "Révision annuelle", OffsetDateTime.now(ZoneOffset.UTC),
                new UUID(0, 0));
    }

    private UUID patient(UUID centre, String nom, String naissance, UUID salle, UUID creneau, UUID generateur,
                         boolean lundiMercredi) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, numero_assurance, "
                        + "date_admission, type_patient, etat_patient, qualite_assure, sous_kt, epo_enabled, salle_id, "
                        + "position_id, generateur_id, jour_lundi, jour_mercredi, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE,?,?,?,?,?,CURRENT_TIMESTAMP)",
                id, centre, "ZT-" + id.toString().substring(0, 8), nom, "Prenom", "M", Date.valueOf(naissance),
                "ASS-" + id.toString().substring(0, 8), Date.valueOf("2026-01-02"), "NON_VACANCIER", "PERMANENT",
                "ASSURE", false, salle, creneau, generateur, lundiMercredi, lundiMercredi);
        return id;
    }

    private UUID infirmier(UUID centre, String matricule, String nom) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO infirmier (id, center_id, matricule, nom, qualification, habilite_isolement, actif) "
                + "VALUES (?, ?, ?, ?, 'INFIRMIER', FALSE, TRUE)", id, centre, matricule, nom);
        return id;
    }
}
