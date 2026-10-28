package com.hemodialyse.backend.contexts.planification.contrat;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.InfirmiersDuCentre;
import com.hemodialyse.backend.contexts.planification.application.LectureDesPreferences;
import com.hemodialyse.backend.contexts.planification.application.PatientsDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.Competence;
import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanning;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanningRepository;
import com.hemodialyse.backend.contexts.planification.domain.PreferenceDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.PreferencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.ProfilDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ProfilsRepository;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesRepository;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuCentre;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyinfirmier.InfirmiersDuCentreJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyorganisation.CreneauxDuCentreJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyorganisation.SallesDuCentreJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient.PatientsDuCentreJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.LectureDesPreferencesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.ParametresDuPlanningJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.PreferencesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.ProfilsJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.ReglagesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.VerrouDuCentreJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat du paramétrage contre l'adaptateur JDBC (H2 et PostgreSQL 16 partagent ces tests) : les dépôts passent le contrat des
 * doubles, plus ce qui n'a de sens que sur une vraie base : aucune ligne créée par une lecture ni par le verrou, colonne
 * historique remise à NULL, lignes anciennes, read model des écrans, ports vers Organisation, Patient et infirmiers, et
 * sérialisation des écritures (verrou de la ligne du centre, clé créée entre-temps).
 */
public abstract class ParametrageJdbcContrat extends ParametrageContrat {

    protected abstract JdbcTemplate jdbc();

    protected abstract TransactionTemplate transaction();

    private SallesDuCentreJdbc salles() {
        return new SallesDuCentreJdbc(jdbc());
    }

    @Override
    protected ParametresDuPlanningRepository parametres() {
        return new ParametresDuPlanningJdbc(jdbc(), salles());
    }

    @Override
    protected ReglagesRepository reglages() {
        return new ReglagesJdbc(jdbc());
    }

    @Override
    protected PreferencesRepository preferences() {
        return new PreferencesJdbc(jdbc());
    }

    @Override
    protected ProfilsRepository profils() {
        return new ProfilsJdbc(jdbc());
    }

    protected VerrouDuCentre verrou() {
        return new VerrouDuCentreJdbc(jdbc());
    }

    @Override
    protected void salle(CentreId centre, SalleId salle, boolean isolement) {
        jdbc().update("INSERT INTO salle (id, center_id, code, nom, isolement) VALUES (?, ?, ?, ?, ?)", salle.valeur(),
                centre.valeur(), "S-" + salle.valeur().toString().substring(30), "Salle", isolement ? "OUI" : "NON");
    }

    private int lignes(String table) {
        return jdbc().queryForObject("SELECT COUNT(*) FROM " + table + " WHERE center_id IN (?, ?)", Integer.class,
                CENTRE.valeur(), AUTRE.valeur());
    }

    private UUID patient(CentreId centre, String nom, String prenom, String etat, UUID creneau, String... jours) {
        UUID id = UUID.randomUUID();
        Set<String> choisis = Set.of(jours);
        jdbc().update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, position_id, jour_dimanche, jour_lundi, jour_mardi, jour_mercredi, jour_jeudi, "
                        + "jour_vendredi, jour_samedi, created_at) VALUES (?, ?, ?, ?, ?, 'M', DATE '2026-01-01', ?, 'NON_VACANCIER', "
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)", id, centre.valeur(), "PLN-" + id, nom, prenom, "ASS-" + id,
                etat, creneau, choisis.contains("dimanche"), choisis.contains("lundi"), choisis.contains("mardi"),
                choisis.contains("mercredi"), choisis.contains("jeudi"), choisis.contains("vendredi"), choisis.contains("samedi"));
        return id;
    }

    private UUID infirmier(CentreId centre, String nom, String prenom, boolean actif) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO infirmier (id, center_id, matricule, nom, prenom, qualification, habilite_isolement, actif) "
                + "VALUES (?, ?, ?, ?, ?, 'INFIRMIER', FALSE, ?)", id, centre.valeur(), "M-" + id, nom, prenom, actif);
        return id;
    }

    // ------------------------------------------------------------------ rien n'est créé sans écriture

    @Test
    void should_create_no_row_when_reading_or_taking_the_centre_lock() {
        parametres().lire(CENTRE);
        reglages().lire(CENTRE);
        reglages().centresEnReplanificationNocturne();
        preferences().duCentre(CENTRE);
        profils().duCentre(CENTRE);
        transaction().executeWithoutResult(statut -> verrou().prendre(CENTRE));

        for (String table : new String[]{"planning_parametres", "planification_reglages", "planning_preference_patient",
                "infirmier_profil_planning"}) {
            assertThat(lignes(table)).as(table).isZero();
        }
    }

    // ------------------------------------------------------------------ lignes anciennes

    @Test
    void should_read_an_old_row_with_empty_ratio_station_and_days_as_the_defaults() {
        jdbc().update("INSERT INTO planning_parametres (center_id, jours_ouverts, patients_par_infirmier, patients_par_poste_serie, "
                + "updated_at) VALUES (?, '', NULL, NULL, CURRENT_TIMESTAMP)", CENTRE.valeur());

        ParametresDuPlanning lus = parametres().lire(CENTRE);

        assertThat(lus.joursOuverts()).hasSize(7);
        assertThat(lus.ratio().patientsParInfirmier()).isEqualTo(4);
        assertThat(lus.patientsParPoste().nombre()).isEqualTo(3);
    }

    @Test
    void should_reset_the_historical_isolation_column_to_null_on_every_write() {
        jdbc().update("INSERT INTO planning_parametres (center_id, jours_ouverts, salles_isolement, updated_at) "
                + "VALUES (?, 'LUNDI', 'valeur-historique', CURRENT_TIMESTAMP)", CENTRE.valeur());

        parametres().enregistrer(CENTRE, ParametresDuPlanning.parDefaut());

        assertThat(jdbc().queryForObject("SELECT salles_isolement FROM planning_parametres WHERE center_id = ?", String.class,
                CENTRE.valeur())).isNull();
    }

    @Test
    void should_read_skills_stored_with_blanks_and_empty_items() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        jdbc().update("INSERT INTO infirmier_profil_planning (center_id, infirmier_id, taux_activite, competences) "
                + "VALUES (?, ?, 40, '   ')", CENTRE.valeur(), a);
        jdbc().update("INSERT INTO infirmier_profil_planning (center_id, infirmier_id, taux_activite, competences) "
                + "VALUES (?, ?, 50, ' CATHETER, ,PEDIATRIE')", CENTRE.valeur(), b);

        assertThat(profils().duCentre(CENTRE).get(new InfirmierId(a)).competences()).isEmpty();
        assertThat(profils().duCentre(CENTRE).get(new InfirmierId(b)).competences())
                .containsExactly(Competence.PEDIATRIE, Competence.CATHETER);
    }

    // ------------------------------------------------------------------ ports vers les autres contextes

    @Test
    void should_list_the_rooms_of_the_centre_and_mark_exactly_the_isolation_rooms() {
        salle(CENTRE, SALLE_1, true);
        salle(CENTRE, SALLE_2, false);
        salle(AUTRE, SALLE_AUTRE, true);

        assertThat(salles().toutes(CENTRE)).containsExactlyInAnyOrder(SALLE_1, SALLE_2);
        assertThat(salles().sallesDIsolement(CENTRE)).containsExactly(SALLE_1);

        salles().marquerIsolement(CENTRE, Set.of(SALLE_2));

        assertThat(salles().sallesDIsolement(CENTRE)).containsExactly(SALLE_2);
        assertThat(salles().sallesDIsolement(AUTRE)).containsExactly(SALLE_AUTRE);
    }

    @Test
    void should_tell_whether_a_slot_belongs_to_the_centre() {
        UUID creneau = UUID.randomUUID();
        jdbc().update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'MATIN', 'Matin')", creneau,
                CENTRE.valeur());

        assertThat(new CreneauxDuCentreJdbc(jdbc()).contient(CENTRE, new CreneauId(creneau))).isTrue();
        assertThat(new CreneauxDuCentreJdbc(jdbc()).contient(AUTRE, new CreneauId(creneau))).isFalse();
    }

    @Test
    void should_tell_the_situation_of_a_patient_and_of_a_nurse_in_the_centre() {
        PatientsDuCentreJdbc patients = new PatientsDuCentreJdbc(jdbc());
        UUID suivi = patient(CENTRE, "Amrani", "Amine", null, null);
        UUID hemodialyse = patient(CENTRE, "Benali", "Karim", "HEMODIALYSE", null);
        InfirmiersDuCentreJdbc infirmiers = new InfirmiersDuCentreJdbc(jdbc());
        UUID actif = infirmier(CENTRE, "Actif", "Un", true);
        UUID inactif = infirmier(CENTRE, "Inactif", "Deux", false);

        assertThat(patients.situation(CENTRE, new PatientId(suivi))).isEqualTo(PatientsDuCentre.Situation.EN_SUIVI);
        assertThat(patients.situation(CENTRE, new PatientId(hemodialyse))).isEqualTo(PatientsDuCentre.Situation.EN_SUIVI);
        for (String sortie : new String[]{"TRANSFERE", "DECEDE", "GREFFE", "GUERRI"}) {
            UUID sorti = patient(CENTRE, "Sorti", sortie, sortie, null);
            assertThat(patients.situation(CENTRE, new PatientId(sorti))).as(sortie).isEqualTo(PatientsDuCentre.Situation.SORTI);
        }
        assertThat(patients.situation(AUTRE, new PatientId(suivi))).isEqualTo(PatientsDuCentre.Situation.INTROUVABLE);
        assertThat(patients.situation(CENTRE, new PatientId(UUID.randomUUID()))).isEqualTo(PatientsDuCentre.Situation.INTROUVABLE);
        assertThat(infirmiers.situation(CENTRE, new InfirmierId(actif))).isEqualTo(InfirmiersDuCentre.Situation.ACTIF);
        assertThat(infirmiers.situation(CENTRE, new InfirmierId(inactif))).isEqualTo(InfirmiersDuCentre.Situation.INACTIF);
        assertThat(infirmiers.situation(AUTRE, new InfirmierId(actif))).isEqualTo(InfirmiersDuCentre.Situation.INTROUVABLE);
    }

    // ------------------------------------------------------------------ read model des écrans

    @Test
    void should_list_the_patients_followed_by_name_with_their_preference_and_hide_those_who_left() {
        UUID matin = UUID.randomUUID();
        jdbc().update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, 'MATIN', 'Matin')", matin,
                CENTRE.valeur());
        UUID benali = patient(CENTRE, "Benali", "Karim", null, matin, "lundi", "mercredi");
        UUID amrani = patient(CENTRE, "Amrani", "Zahra", "HEMODIALYSE", null, "mardi");
        patient(CENTRE, "Sorti", "Un", "TRANSFERE", null);
        patient(AUTRE, "Etranger", "Un", null, null);
        preferences().enregistrer(CENTRE, new PreferenceDePlanification(new PatientId(benali), new CreneauId(matin), 3, true));

        PageDeResultats<LectureDesPreferences.LigneDePatient> page = new LectureDesPreferencesJdbc(jdbc())
                .patientsSuivis(CENTRE, new Pagination(0, 20));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.taille()).isEqualTo(20);
        assertThat(page.elements()).extracting(LectureDesPreferences.LigneDePatient::nom).containsExactly("Zahra Amrani", "Karim Benali");
        assertThat(page.elements().get(0).patient()).isEqualTo(new PatientId(amrani));
        assertThat(page.elements().get(0).preference()).isEqualTo(PreferenceDePlanification.aucune(new PatientId(amrani)));
        assertThat(page.elements().get(1).jours()).extracting(Enum::name).containsExactly("LUNDI", "MERCREDI");
        assertThat(page.elements().get(1).creneauActuel()).isEqualTo(new CreneauId(matin));
        assertThat(page.elements().get(1).preference().seancesParSemaine()).isEqualTo(3);
    }

    @Test
    void should_paginate_the_patients_and_keep_the_total() {
        for (int i = 1; i <= 5; i++) patient(CENTRE, "Nom" + i, "Prenom", null, null);
        LectureDesPreferencesJdbc lecture = new LectureDesPreferencesJdbc(jdbc());

        assertThat(lecture.patientsSuivis(CENTRE, new Pagination(1, 2)).elements()).extracting(LectureDesPreferences.LigneDePatient::nom)
                .containsExactly("Prenom Nom3", "Prenom Nom4");
        assertThat(lecture.patientsSuivis(CENTRE, new Pagination(9, 2)).elements()).isEmpty();
        PageDeResultats<LectureDesPreferences.LigneDePatient> vide = lecture.patientsSuivis(CENTRE, new Pagination(0, 0));
        assertThat(vide.elements()).isEmpty();
        assertThat(vide.total()).isEqualTo(5);
        assertThat(lecture.patientsSuivis(CENTRE, new Pagination(0, 500)).taille()).isEqualTo(100);
    }

    @Test
    void should_list_the_active_nurses_by_name_with_the_default_profile_when_none_is_recorded() {
        UUID benali = infirmier(CENTRE, "Benali", "Samia", true);
        UUID amrani = infirmier(CENTRE, "Amrani", "Nadia", true);
        infirmier(CENTRE, "Inactif", "Un", false);
        infirmier(AUTRE, "Etranger", "Un", true);
        profils().enregistrer(CENTRE, new ProfilDePlanification(new InfirmierId(benali), 70, Set.of(Competence.CATHETER)));

        PageDeResultats<LectureDesPreferences.LigneDInfirmier> page = new LectureDesPreferencesJdbc(jdbc())
                .infirmiersActifs(CENTRE, new Pagination(0, 20));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.elements()).extracting(LectureDesPreferences.LigneDInfirmier::nom).containsExactly("Nadia Amrani", "Samia Benali");
        assertThat(page.elements().get(0).profil()).isEqualTo(ProfilDePlanification.parDefaut(new InfirmierId(amrani)));
        assertThat(page.elements().get(0).qualification()).isEqualTo("INFIRMIER");
        assertThat(page.elements().get(1).profil().tauxActivite()).isEqualTo(70);
    }

    // ------------------------------------------------------------------ sérialisation des écritures

    protected static void attendre(CountDownLatch verrou) {
        try {
            verrou.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected void ligneDuCentre() {
        parametres().enregistrer(CENTRE, ParametresDuPlanning.parDefaut());
    }

    @Test
    void should_make_a_second_write_wait_for_the_centre_lock_then_let_it_through() throws Exception {
        ligneDuCentre();
        CountDownLatch verrouille = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> detenteur = pool.submit(() -> transaction().execute(statut -> {
                verrou().prendre(CENTRE);
                verrouille.countDown();
                attendre(liberer);
                return "libere";
            }));
            assertThat(verrouille.await(20, TimeUnit.SECONDS)).isTrue();
            Future<String> suivant = pool.submit(() -> transaction().execute(statut -> {
                verrou().prendre(CENTRE);
                return "passe";
            }));
            assertThatThrownBy(() -> suivant.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            liberer.countDown();
            assertThat(detenteur.get(20, TimeUnit.SECONDS)).isEqualTo("libere");
            assertThat(suivant.get(20, TimeUnit.SECONDS)).isEqualTo("passe");
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void should_not_make_another_centre_wait_for_the_lock() throws Exception {
        ligneDuCentre();
        parametres().enregistrer(AUTRE, ParametresDuPlanning.parDefaut());
        CountDownLatch verrouille = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            Future<?> detenteur = pool.submit(() -> transaction().execute(statut -> {
                verrou().prendre(CENTRE);
                verrouille.countDown();
                attendre(liberer);
                return null;
            }));
            assertThat(verrouille.await(20, TimeUnit.SECONDS)).isTrue();

            transaction().executeWithoutResult(statut -> verrou().prendre(AUTRE));

            liberer.countDown();
            detenteur.get(20, TimeUnit.SECONDS);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void should_answer_a_concurrent_write_when_the_same_key_is_created_in_the_meantime() throws Exception {
        CountDownLatch creee = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> premiere = pool.submit(() -> transaction().execute(statut -> {
                reglages().enregistrer(CENTRE, new ReglagesDePlanification(true, 6, 35, 2));
                creee.countDown();
                attendre(liberer);
                return null;
            }));
            assertThat(creee.await(20, TimeUnit.SECONDS)).isTrue();
            Future<?> seconde = pool.submit(() -> transaction().execute(statut -> {
                reglages().enregistrer(CENTRE, new ReglagesDePlanification(false, 4, 30, 0));
                return null;
            }));
            assertThatThrownBy(() -> seconde.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            liberer.countDown();
            premiere.get(20, TimeUnit.SECONDS);
            assertThatThrownBy(() -> seconde.get(20, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(EcritureConcurrente.class);
            assertThat(reglages().lire(CENTRE)).isEqualTo(new ReglagesDePlanification(true, 6, 35, 2));
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }
}
