package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.planning.optimisation.OptimisationApplicationService;
import com.hemodialyse.backend.application.planning.optimisation.OptimisationPlanningService;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation.PatientAPlacer;
import com.hemodialyse.backend.domain.planning.optimisation.model.ObjectifInfirmiers;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation.StatutRun;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationDonneesPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationRunRepositoryPort;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Optimisation du planning de bout en bout sur la base H2 : lecture des données du centre (jamais celles d'un autre
 * centre), calcul Timefold réel, historique, application de la proposition (patients, roulement) et refus d'une
 * proposition périmée.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PlanningOptimisationIntegrationTest {

    private static final UUID SOC = UUID.fromString("99997000-0000-0000-0000-000000000001");
    private static final UUID C1 = UUID.fromString("99997000-0000-0000-0000-0000000000c1");
    private static final UUID C2 = UUID.fromString("99997000-0000-0000-0000-0000000000c2");
    private static final LocalDate DIMANCHE = LocalDate.of(2026, 9, 27);

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

    private UUID salleA;
    private UUID salleB;
    private UUID matin;
    private UUID p1;
    private UUID p2;
    private UUID p3;
    private UUID p4;
    private UUID attente;
    private UUID autreCentrePatient;
    private UUID marie;
    private UUID paul;

    @BeforeEach
    void setup() {
        cleanup();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC, "ZT-OPT1", "Société OPT1");
        centre(C1, "ZT-OPT1-A");
        centre(C2, "ZT-OPT1-B");
        salleA = salle(C1, "ZT-OA", "Salle A");
        salleB = salle(C1, "ZT-OB", "Salle B");
        matin = creneau(C1, "ZT-OP1", "Matin");
        UUID salleAutre = salle(C2, "ZT-OC", "Salle autre centre");
        UUID matinAutre = creneau(C2, "ZT-OP2", "Matin autre centre");

        UUID a1 = generateur(C1, "ZT-A1", salleA);
        UUID a2 = generateur(C1, "ZT-A2", salleA);
        generateur(C1, "ZT-A3", salleA);
        generateur(C1, "ZT-A4", salleA);
        generateur(C1, "ZT-A5", salleA);
        UUID b1 = generateur(C1, "ZT-B1", salleB);
        UUID b2 = generateur(C1, "ZT-B2", salleB);
        generateur(C1, "ZT-B3", salleB);
        generateur(C1, "ZT-B4", salleB);
        generateur(C1, "ZT-B5", salleB);
        UUID autreGen = generateur(C2, "ZT-C1", salleAutre);

        // 2 patients par salle, tous le lundi et le mercredi : regroupés dans une salle, il faut moitié moins d'infirmiers
        p1 = patient(C1, "Alpha", salleA, matin, a1, true);
        p2 = patient(C1, "Bravo", salleA, matin, a2, true);
        p3 = patient(C1, "Charlie", salleB, matin, b1, true);
        p4 = patient(C1, "Delta", salleB, matin, b2, true);
        attente = patient(C1, "Echo", null, null, null, true);
        autreCentrePatient = patient(C2, "Etranger", salleAutre, matinAutre, autreGen, true);

        marie = infirmier(C1, "ZT-I1", "Marie");
        paul = infirmier(C1, "ZT-I2", "Paul");
        infirmier(C2, "ZT-I1", "Etrangère");
        affectation(C1, marie, salleA, matin, "LUNDI,MERCREDI");
        affectation(C1, paul, salleB, matin, "LUNDI,MERCREDI");
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("planification_optimisation", "infirmier_remplacement", "infirmier_absence",
                "infirmier_affectation", "infirmier", "patients", "position_creneau", "salle")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", C1, C2);
        }
        jdbc.update("DELETE FROM gmao_equipements WHERE centre_id IN (?, ?)", C1, C2);
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-OPT1%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-OPT1%'");
    }

    private ParametresOptimisation parametres(PerimetreOptimisation perimetre, int stabilite) {
        return new ParametresOptimisation(perimetre, DIMANCHE, 1, 2, stabilite, ObjectifInfirmiers.EQUITE, 2, 6);
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

    private Map<String, String> salleDesPatients() {
        return jdbc.query("SELECT nom, salle_id FROM patients WHERE center_id = ? AND salle_id IS NOT NULL", rs -> {
            java.util.Map<String, String> m = new java.util.HashMap<>();
            while (rs.next()) m.put(rs.getString("nom"), rs.getString("salle_id"));
            return m;
        }, C1);
    }

    @Test
    void should_read_only_the_data_of_the_requested_center_including_the_patients_waiting_for_a_place() {
        DonneesOptimisation lues = donnees.charger(C1, DIMANCHE, DIMANCHE.plusDays(6));

        assertThat(lues.patients()).extracting(PatientAPlacer::nom)
                .containsExactlyInAnyOrder("Prenom Alpha", "Prenom Bravo", "Prenom Charlie", "Prenom Delta", "Prenom Echo");
        PatientAPlacer enAttente = lues.patients().stream().filter(p -> p.nom().endsWith("Echo")).findFirst().orElseThrow();
        assertThat(enAttente.actuelle()).isNull();
        assertThat(enAttente.jours()).containsExactlyInAnyOrder(JourSemaine.LUNDI, JourSemaine.MERCREDI);
        PatientAPlacer place = lues.patients().stream().filter(p -> p.nom().endsWith("Alpha")).findFirst().orElseThrow();
        assertThat(place.actuelle().salleId()).isEqualTo(salleA);
        assertThat(place.actuelle().generateurCode()).isEqualTo("ZT-A1");
        assertThat(lues.planning().salles()).hasSize(2);
        assertThat(lues.planning().generateurs()).hasSize(10);
        assertThat(lues.presence().infirmiers()).extracting(i -> i.nom()).containsExactlyInAnyOrder("Marie", "Paul");
        assertThat(lues.patients()).extracting(PatientAPlacer::patientId).doesNotContain(autreCentrePatient);
    }

    @Test
    void should_optimise_then_apply_the_patients_of_the_center_without_touching_the_others() throws Exception {
        RunOptimisation lancee = planification.lancer(C1, parametres(PerimetreOptimisation.PATIENTS, 1), "admin");
        assertThat(lancee.enCours()).isTrue();
        assertThatThrownBy(() -> planification.lancer(C1, parametres(PerimetreOptimisation.PATIENTS, 1), "admin"))
                .isInstanceOf(BusinessException.class);

        RunOptimisation fini = attendre(lancee.id());

        assertThat(fini.statut()).isEqualTo(StatutRun.TERMINEE);
        assertThat(fini.resultat()).isNotNull();
        assertThat(fini.resume().avant().patientsNonPlaces()).isEqualTo(1);
        assertThat(fini.resume().apres().patientsNonPlaces()).isZero();
        assertThat(fini.resume().avant().sallesOuvertes()).isEqualTo(4);
        assertThat(fini.resume().apres().sallesOuvertes()).isEqualTo(2);
        assertThat(fini.resultat().nonPlaces()).isEmpty();
        // Echo, en attente, reçoit une place ; les 5 patients tiennent dans une seule salle
        assertThat(fini.resultat().deplacements()).extracting(d -> d.patientId()).contains(attente);

        assertThatThrownBy(() -> planification.consulter(C2, lancee.id()))
                .isInstanceOf(BusinessException.class);
        assertThat(planification.historique(C2, 0, 20).items()).isEmpty();
        assertThat(planification.historique(C1, 0, 20).items()).singleElement().satisfies(h -> {
            assertThat(h.id()).isEqualTo(lancee.id());
            assertThat(h.resultat()).as("l'historique ne charge pas le détail").isNull();
            assertThat(h.resume()).isNotNull();
        });

        application.appliquer(C1, lancee.id());

        Map<String, String> salles = salleDesPatients();
        assertThat(salles).containsKeys("Alpha", "Bravo", "Charlie", "Delta", "Echo");
        assertThat(salles.get("Echo")).isNotNull();
        assertThat(java.util.stream.Stream.of("Alpha", "Bravo", "Charlie", "Delta", "Echo").map(salles::get).distinct())
                .as("les cinq patients de 2 jours tiennent dans une seule salle").hasSize(1);
        assertThat(jdbc.queryForObject("SELECT salle_id FROM patients WHERE id = ?", UUID.class, autreCentrePatient))
                .as("le patient d'un autre centre n'est pas touché").isNotNull();
        assertThat(runs.findById(C1, lancee.id()).orElseThrow().appliqueLe()).isNotNull();
        assertThatThrownBy(() -> application.appliquer(C1, lancee.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> application.appliquer(C2, lancee.id())).isInstanceOf(BusinessException.class);
    }

    @Test
    void should_refuse_to_apply_a_proposal_when_a_patient_changed_after_the_calculation() throws Exception {
        RunOptimisation fini = attendre(planification.lancer(C1, parametres(PerimetreOptimisation.PATIENTS, 1), "admin").id());
        jdbc.update("UPDATE patients SET jour_vendredi = TRUE WHERE id = ?", p1);

        assertThatThrownBy(() -> application.appliquer(C1, fini.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("OPTIMISATION_PERIMEE");
        assertThat(salleDesPatients()).doesNotContainKey("Echo");
        assertThat(runs.findById(C1, fini.id()).orElseThrow().appliqueLe()).isNull();
    }

    @Test
    void should_design_and_apply_the_rotation_of_the_center() throws Exception {
        RunOptimisation fini = attendre(planification.lancer(C1, parametres(PerimetreOptimisation.ROULEMENT, 5), "admin").id());

        // 4 patients répartis en 2 salles, lundi et mercredi : 4 vacations ; Marie et Paul tiennent chacun deux jours
        assertThat(fini.resultat().manques()).isEmpty();
        assertThat(fini.resultat().vacations()).hasSize(4);
        assertThat(fini.resume().avant().vacationsNonPourvues()).isZero();

        application.appliquer(C1, fini.id());

        List<String> roulement = jdbc.query("SELECT infirmier_id, salle_id, jours FROM infirmier_affectation WHERE center_id = ?",
                (rs, i) -> rs.getString("infirmier_id") + "|" + rs.getString("salle_id") + "|" + rs.getString("jours"), C1);
        assertThat(roulement).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM infirmier_affectation WHERE center_id = ?", Long.class, C2))
                .isZero();
    }

    @Test
    void should_cover_the_absence_with_a_replacement_over_the_horizon() throws Exception {
        UUID lea = infirmier(C1, "ZT-I3", "Léa");
        jdbc.update("INSERT INTO infirmier_absence (id, center_id, infirmier_id, date_debut, date_fin, type) "
                + "VALUES (?, ?, ?, ?, ?, 'CONGE')", UUID.randomUUID(), C1, marie, Date.valueOf(DIMANCHE),
                Date.valueOf(DIMANCHE.plusDays(6)));
        // Echo reste sans place : la couverture ne compte que les patients placés

        RunOptimisation fini = attendre(planification.lancer(C1, new ParametresOptimisation(PerimetreOptimisation.COUVERTURE,
                DIMANCHE, 1, 2, 5, ObjectifInfirmiers.EQUITE, 2, 6), "admin").id());

        assertThat(fini.resultat().manques()).isEmpty();
        assertThat(fini.resultat().vacations().stream().filter(v -> !v.existante()))
                .extracting(v -> v.infirmierId()).containsOnly(lea).hasSize(2);

        application.appliquer(C1, fini.id());

        List<UUID> remplacants = jdbc.query("SELECT infirmier_id FROM infirmier_remplacement WHERE center_id = ?",
                (rs, i) -> rs.getObject(1, UUID.class), C1);
        assertThat(remplacants).containsExactly(lea, lea);
    }

    @Test
    void should_fail_the_runs_orphaned_by_a_restart_and_free_the_center() {
        RunOptimisation orpheline = runs.save(RunOptimisation.demarrer(C1, parametres(PerimetreOptimisation.PATIENTS, 5),
                "admin", "x", java.time.Instant.now()));
        assertThat(runs.findEnCours(C1)).isPresent();

        assertThat(runs.interrompreEnCours("redémarrage")).isGreaterThanOrEqualTo(1);

        assertThat(runs.findById(C1, orpheline.id()).orElseThrow()).satisfies(r -> {
            assertThat(r.statut()).isEqualTo(StatutRun.ECHEC);
            assertThat(r.erreur()).isEqualTo("redémarrage");
        });
        assertThat(runs.findEnCours(C1)).isEmpty();
    }

    @Test
    void should_keep_only_the_most_recent_runs_of_a_center() {
        for (int i = 0; i < 4; i++) {
            runs.save(RunOptimisation.demarrer(C1, parametres(PerimetreOptimisation.PATIENTS, 5), "admin", "x",
                    java.time.Instant.now().plusSeconds(i)).echouer("e", java.time.Instant.now()));
        }
        runs.save(RunOptimisation.demarrer(C2, parametres(PerimetreOptimisation.PATIENTS, 5), "admin", "x",
                java.time.Instant.now()).echouer("e", java.time.Instant.now()));

        runs.purger(C1, 2);

        assertThat(runs.findPaged(C1, 0, 10).total()).isEqualTo(2);
        assertThat(runs.findPaged(C2, 0, 10).total()).as("un autre centre n'est pas purgé").isEqualTo(1);
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

    private UUID patient(UUID centre, String nom, UUID salle, UUID creneau, UUID generateur, boolean lundiMercredi) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, numero_assurance, "
                        + "date_admission, type_patient, etat_patient, qualite_assure, sous_kt, epo_enabled, salle_id, "
                        + "position_id, generateur_id, jour_lundi, jour_mercredi, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE,?,?,?,?,?,CURRENT_TIMESTAMP)",
                id, centre, "ZT-" + id.toString().substring(0, 8), nom, "Prenom", "M", Date.valueOf("1970-05-05"),
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

    private void affectation(UUID centre, UUID infirmier, UUID salle, UUID creneau, String jours) {
        jdbc.update("INSERT INTO infirmier_affectation (id, center_id, infirmier_id, salle_id, creneau_id, jours) "
                + "VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), centre, infirmier, salle, creneau, jours);
    }
}
