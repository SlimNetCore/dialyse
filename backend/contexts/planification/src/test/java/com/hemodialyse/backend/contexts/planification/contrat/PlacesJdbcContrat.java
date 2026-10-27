package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.LectureDesRessources;
import com.hemodialyse.backend.contexts.planification.application.NomsDesPatients;
import com.hemodialyse.backend.contexts.planification.application.PlacerLePatient;
import com.hemodialyse.backend.contexts.planification.application.RisquesInfectieux;
import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Place;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacydossiermedical.RisquesInfectieuxJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient.NomsDesPatientsJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient.PlacesDuCentreJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.VerrouDesPlacesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacementDesPatients;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat des places contre les adapters JDBC (H2 et PostgreSQL 16 partagent ces tests) : les adapters passent le contrat du double,
 * plus ce qui n'a de sens que sur une vraie base : création de la ligne de verrou, exclusion mutuelle, séjours limités, sérologies
 * départagées par date, et la course de deux enregistrements sur la même place.
 */
abstract class PlacesJdbcContrat extends PlacesContrat {

    protected static final UUID AUTRE_CENTRE = UUID.fromString("99997805-0000-0000-0000-00000000000b");

    protected abstract JdbcTemplate jdbc();

    protected abstract TransactionTemplate transaction();

    /** Unité de travail du verrou : {@code lock_timeout} court sur PostgreSQL, délai propre à H2 sinon. */
    protected UniteDeTravailSpring unite() {
        return new UniteDeTravailSpring(transaction(), jdbc(), Duration.ofMillis(500));
    }

    @Override
    protected PlacesDuCentreRepository places() {
        return new PlacesDuCentreJdbc(jdbc());
    }

    @Override
    protected RisquesInfectieux risques() {
        return new RisquesInfectieuxJdbc(jdbc());
    }

    @Override
    protected NomsDesPatients noms() {
        return new NomsDesPatientsJdbc(jdbc());
    }

    @Override
    protected VerrouDesPlaces verrou() {
        return new VerrouDesPlacesJdbc(jdbc());
    }

    @Override
    protected void patient(PatientId patient, String nomAffiche, Place placement) {
        inserer(CENTRE.valeur(), patient, nomAffiche, placement, null, null);
    }

    @Override
    protected void patientDUnAutreCentre(PatientId patient, String nomAffiche, Place placement) {
        inserer(AUTRE_CENTRE, patient, nomAffiche, placement, null, null);
    }

    @Override
    protected void sortir(PatientId patient) {
        jdbc().update("UPDATE patients SET etat_patient = 'TRANSFERE', date_evenement_etat = ? WHERE center_id = ? AND id = ?",
                Date.valueOf(aujourdhui().minusDays(1)), CENTRE.valeur(), patient.valeur());
    }

    @Override
    protected void serologiePositive(PatientId patient) {
        serologie(patient, "AG_HBS", "POSITIF", aujourdhui().minusDays(10), Instant.parse("2026-10-17T08:00:00Z"));
    }

    protected final void serologie(PatientId patient, String marqueur, String resultat, LocalDate prelevement, Instant saisie) {
        jdbc().update("INSERT INTO serologies_patient (id, center_id, patient_id, marqueur, resultat, date_prelevement, created_at, "
                        + "updated_at) VALUES (?, (SELECT center_id FROM patients WHERE id = ?), ?, ?, ?, ?, ?, ?)", UUID.randomUUID(),
                patient.valeur(), patient.valeur(), marqueur,
                resultat, Date.valueOf(prelevement), Timestamp.from(saisie), Timestamp.from(saisie));
    }

    protected final void inserer(UUID centre, PatientId patient, String nomAffiche, Place placement, String etat, LocalDate evenement) {
        String[] morceaux = nomAffiche.split(" ", 2);
        StringBuilder colonnes = new StringBuilder("id, center_id, nom, prenom, date_admission, numero_assurance, sexe, type_patient, "
                + "etat_patient, date_evenement_etat, salle_id, position_id, generateur_id");
        StringBuilder valeurs = new StringBuilder("?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?");
        List<Object> args = new ArrayList<>(List.of(patient.valeur(), centre, morceaux[0], morceaux.length > 1 ? morceaux[1] : "",
                Date.valueOf(aujourdhui().minusYears(1)), "N-" + patient.valeur(), "F", "HEMODIALYSE"));
        args.add(etat);
        args.add(evenement == null ? null : Date.valueOf(evenement));
        args.add(placement == null ? null : placement.salle().valeur());
        args.add(placement == null ? null : placement.creneau().valeur());
        args.add(placement == null ? null : placement.generateur().valeur());
        for (JourDeLaSemaine jour : JourDeLaSemaine.values()) {
            colonnes.append(", jour_").append(jour.name().toLowerCase(Locale.ROOT));
            valeurs.append(", ?");
            args.add(placement != null && placement.jours().contains(jour));
        }
        jdbc().update("INSERT INTO patients (" + colonnes + ") VALUES (" + valeurs + ")", args.toArray());
    }

    protected final long lignesDeParametres() {
        Long n = jdbc().queryForObject("SELECT COUNT(*) FROM planning_parametres WHERE center_id = ?", Long.class, CENTRE.valeur());
        return n == null ? 0 : n;
    }

    // ───────────── ce qui n'a de sens que sur une vraie base ─────────────

    @Test
    void should_create_the_default_row_of_the_lock_when_the_centre_was_never_configured() {
        assertThat(lignesDeParametres()).isZero();

        transaction().executeWithoutResult(statut -> verrou().prendre(CENTRE));

        assertThat(lignesDeParametres()).isOne();
        assertThat(jdbc().queryForObject("SELECT jours_ouverts FROM planning_parametres WHERE center_id = ?", String.class,
                CENTRE.valeur())).contains("LUNDI").contains("SAMEDI").contains("DIMANCHE");
    }

    @Test
    void should_keep_an_existing_row_of_the_lock_untouched() {
        jdbc().update("INSERT INTO planning_parametres (center_id, jours_ouverts, patients_par_infirmier, patients_par_poste_serie, "
                + "updated_at) VALUES (?, 'LUNDI,MARDI', 7, 4, ?)", CENTRE.valeur(), Timestamp.from(Instant.now()));

        transaction().executeWithoutResult(statut -> verrou().prendre(CENTRE));

        assertThat(lignesDeParametres()).isOne();
        assertThat(jdbc().queryForObject("SELECT jours_ouverts FROM planning_parametres WHERE center_id = ?", String.class,
                CENTRE.valeur())).isEqualTo("LUNDI,MARDI");
    }

    @Test
    void should_free_the_place_of_a_stay_that_ended_but_keep_one_ending_today_and_a_permanent_patient() {
        inserer(CENTRE.valeur(), ALICE, "DUPONT Alice", place(SALLE, GENERATEUR, JourDeLaSemaine.LUNDI),
                "OCCASIONNEL", aujourdhui().minusDays(1));
        inserer(CENTRE.valeur(), BORIS, "MARTIN Boris", place(SALLE, GENERATEUR_2, JourDeLaSemaine.MARDI),
                "OCCASIONNEL", aujourdhui());
        inserer(CENTRE.valeur(), CHLOE, "BERNARD Chloe", place(SALLE_2, GENERATEUR_3, JourDeLaSemaine.MARDI),
                "PERMANENT", null);
        inserer(CENTRE.valeur(), DAMIEN, "LEROY Damien", place(SALLE_2, GENERATEUR_3, JourDeLaSemaine.JEUDI),
                "DECEDE", aujourdhui());

        var occupants = places().charger(CENTRE, RIEN, aujourdhui()).occupations().stream()
                .map(o -> o.patient()).toList();

        // ALICE : fin de séjour hier, libérée. DAMIEN : décédé aujourd'hui, dernier jour occupé = hier, libérée.
        assertThat(occupants).containsExactlyInAnyOrder(BORIS, CHLOE);
    }

    @Test
    void should_cite_a_patient_of_a_generator_even_without_days_as_the_legacy_does() {
        inserer(CENTRE.valeur(), ALICE, "DUPONT Alice", null, null, null);
        jdbc().update("UPDATE patients SET generateur_id = ? WHERE id = ?", GENERATEUR.valeur(), ALICE.valeur());

        assertThat(places().patientsDuGenerateur(CENTRE, GENERATEUR, aujourdhui())).containsExactly(ALICE);
        assertThat(places().charger(CENTRE, RIEN, aujourdhui()).occupations()).isEmpty();
    }

    @Test
    void should_rank_the_serologies_of_a_marker_by_sampling_date_then_by_entry_date() {
        patient(ALICE, "DUPONT Alice", place(SALLE, GENERATEUR, JourDeLaSemaine.LUNDI));
        patient(BORIS, "MARTIN Boris", place(SALLE, GENERATEUR_2, JourDeLaSemaine.MARDI));
        // ALICE : positive puis négative plus récente = sans risque ; BORIS : négative puis positive plus récente = à risque
        serologie(ALICE, "VIH_AC", "POSITIF", aujourdhui().minusDays(30), Instant.parse("2026-09-27T08:00:00Z"));
        serologie(ALICE, "VIH_AC", "NEGATIF", aujourdhui().minusDays(5), Instant.parse("2026-10-22T08:00:00Z"));
        serologie(BORIS, "ARN_VHC", "NEGATIF", aujourdhui().minusDays(30), Instant.parse("2026-09-27T08:00:00Z"));
        serologie(BORIS, "ARN_VHC", "POSITIF", aujourdhui().minusDays(5), Instant.parse("2026-10-22T08:00:00Z"));
        // un marqueur hors liste ne compte pas
        serologie(ALICE, "AC_HBS", "POSITIF", aujourdhui().minusDays(2), Instant.parse("2026-10-25T08:00:00Z"));

        assertThat(risques().de(CENTRE, ALICE).aRisque()).isFalse();
        assertThat(risques().de(CENTRE, BORIS).aRisque()).isTrue();
    }

    @Test
    void should_refuse_the_lock_while_another_transaction_holds_it() throws Exception {
        transaction().executeWithoutResult(statut -> verrou().prendre(CENTRE));
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

            assertThatThrownBy(() -> unite().executer(() -> {
                verrou().prendre(CENTRE);
                return null;
            })).isInstanceOf(EcritureConcurrente.class);

            liberer.countDown();
            detenteur.get(20, TimeUnit.SECONDS);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }

    /**
     * Course de deux enregistrements de deux patients sur la même place (même générateur, même jour), le centre déjà paramétré :
     * le verrou les sérialise, un seul est accepté et l'autre reçoit le refus {@code PLACEMENT_GENERATEUR_OCCUPE} (jamais deux
     * patients sur le même générateur).
     */
    @Test
    void should_accept_exactly_one_of_two_concurrent_placements_on_the_same_place() throws Exception {
        transaction().executeWithoutResult(statut -> verrou().prendre(CENTRE));
        courseSurLaMemePlace(true);
    }

    /** Même course quand la ligne de verrou n'existe pas : au moins un accepté, jamais deux sur la même place. */
    @Test
    void should_never_double_book_a_place_when_the_lock_row_is_created_by_the_race() throws Exception {
        courseSurLaMemePlace(false);
    }

    private void courseSurLaMemePlace(boolean verrouDejaCree) throws Exception {
        patient(ALICE, "DUPONT Alice", null);
        patient(BORIS, "MARTIN Boris", null);
        LectureDesRessources ressources = (centre, jour) -> ressourcesPour();
        UniteDeTravailSpring unite = new UniteDeTravailSpring(transaction(), jdbc(), Duration.ofSeconds(5));
        PlacerLePatient placer = new PlacerLePatient(unite, verrou(), places(), ressources, risques(), centre -> aujourdhui());
        CyclicBarrier depart = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> issues = new ArrayList<>();
            for (PatientId patient : List.of(ALICE, BORIS)) {
                issues.add(pool.submit(() -> {
                    depart.await(20, TimeUnit.SECONDS);
                    try {
                        placer.placer(CENTRE.valeur(), patient.valeur(), SALLE.valeur(), CRENEAU.valeur(), GENERATEUR.valeur(),
                                Set.of(DayOfWeek.MONDAY));
                        return "ACCEPTE";
                    } catch (PlacementDesPatients.Refus refus) {
                        return refus.code();
                    } catch (PlacementDesPatients.EcritureConcurrente concurrente) {
                        return "CONCURRENT";
                    }
                }));
            }
            List<String> resultats = new ArrayList<>();
            for (Future<String> issue : issues) resultats.add(issue.get(60, TimeUnit.SECONDS));

            assertThat(resultats).filteredOn("ACCEPTE"::equals).hasSize(1);
            String perdant = resultats.stream().filter(r -> !r.equals("ACCEPTE")).findFirst().orElseThrow();
            if (verrouDejaCree) {
                assertThat(perdant).isEqualTo("PLACEMENT_GENERATEUR_OCCUPE");
            } else {
                assertThat(perdant).isIn("PLACEMENT_GENERATEUR_OCCUPE", "CONCURRENT");
            }
            long surLeGenerateur = places().charger(CENTRE, RIEN, aujourdhui()).occupations().stream()
                    .filter(o -> GENERATEUR.equals(o.generateur())).count();
            assertThat(surLeGenerateur).isOne();
        } finally {
            pool.shutdownNow();
        }
    }

    // ───────────── revue P5 : le délai de verrou vaut aussi dans une transaction ouverte par l'APPELANT ─────────────
    // Les ACL de la fiche et de la sérologie (plateforme) et le @Transactional de l'optimisation ouvrent la transaction ELLES-MÊMES ;
    // l'unité de travail du contexte la rejoint. Sous PostgreSQL (lock_timeout = 0 par défaut) le verrou attendait sans limite.

    /** Une transaction concurrente tient le verrou du centre pendant l'épreuve, qui doit finir en 409 en moins de 15 s. */
    private void refuseePendantQuUneAutreEcritureTientLeVerrou(java.util.concurrent.Callable<Object> epreuve) throws Exception {
        transaction().executeWithoutResult(statut -> verrou().prendre(CENTRE));
        CountDownLatch verrouille = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> detenteur = pool.submit(() -> transaction().execute(statut -> {
                verrou().prendre(CENTRE);
                verrouille.countDown();
                attendre(liberer);
                return null;
            }));
            assertThat(verrouille.await(20, TimeUnit.SECONDS)).isTrue();

            Future<Object> demandeur = pool.submit(epreuve);
            assertThatThrownBy(() -> demandeur.get(15, TimeUnit.SECONDS))
                    .as("409 attendu : le verrou ne doit jamais être attendu sans limite")
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .cause().isInstanceOf(PlacementDesPatients.EcritureConcurrente.class);

            liberer.countDown();
            detenteur.get(20, TimeUnit.SECONDS);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }

    private PlacerLePatient placerLePatient(UniteDeTravailSpring unite) {
        return new PlacerLePatient(unite, verrou(), places(), (centre, jour) -> ressourcesPour(), risques(), centre -> aujourdhui());
    }

    @Test
    void should_refuse_a_card_check_after_the_lock_timeout_when_the_caller_opened_the_transaction() throws Exception {
        PlacerLePatient placer = placerLePatient(unite());
        refuseePendantQuUneAutreEcritureTientLeVerrou(() -> transaction().execute(statut -> {
            placer.verifier(CENTRE.valeur(), null, SALLE.valeur(), CRENEAU.valeur(), GENERATEUR.valeur(), Set.of(DayOfWeek.MONDAY));
            return null;
        }));
    }

    @Test
    void should_refuse_a_serology_reassignment_after_the_lock_timeout_when_the_caller_opened_the_transaction() throws Exception {
        patient(ALICE, "DUPONT Alice", null);
        serologiePositive(ALICE);
        com.hemodialyse.backend.contexts.planification.application.NotificationsDuPlacement muettes = new AlertesMuettes();
        var reaffecter = new com.hemodialyse.backend.contexts.planification.application.ReaffecterEnIsolementSiRisque(unite(), verrou(),
                places(), (centre, jour) -> ressourcesPour(), risques(),
                (centre, patient) -> com.hemodialyse.backend.contexts.planification.application.PatientsDuCentre.Situation.EN_SUIVI,
                noms(), muettes, centre -> aujourdhui());
        refuseePendantQuUneAutreEcritureTientLeVerrou(() -> transaction().execute(statut -> {
            reaffecter.siRisque(CENTRE.valeur(), ALICE.valeur());
            return null;
        }));
    }

    @Test
    void should_refuse_the_optimisation_lock_after_the_timeout_when_the_legacy_transaction_is_already_open() throws Exception {
        var verrouDesPlaces = new com.hemodialyse.backend.contexts.planification.application.VerrouillerLesPlaces(unite(), verrou());
        refuseePendantQuUneAutreEcritureTientLeVerrou(() -> transaction().execute(statut -> {
            verrouDesPlaces.verrouiller(CENTRE.valeur());
            return null;
        }));
    }

    /**
     * Course fiche contre optimisation : l'optimisation (transaction du legacy) verrouille les places par le port publié puis écrit la
     * place d'Alice ; la fiche de Boris, sur le même générateur le même jour, attend le verrou puis, une fois l'optimisation validée,
     * voit la place prise et est refusée. Sans le verrou, la fiche lirait l'état d'avant et les deux occuperaient le générateur.
     */
    @Test
    void should_make_a_card_wait_for_an_optimisation_that_holds_the_lock_then_refuse_the_place_it_took() throws Exception {
        patient(ALICE, "DUPONT Alice", null);
        patient(BORIS, "MARTIN Boris", null);
        transaction().executeWithoutResult(statut -> verrou().prendre(CENTRE));
        var verrouDesPlaces = new com.hemodialyse.backend.contexts.planification.application.VerrouillerLesPlaces(
                new UniteDeTravailSpring(transaction(), jdbc(), Duration.ofSeconds(5)), verrou());
        PlacerLePatient placer = placerLePatient(new UniteDeTravailSpring(transaction(), jdbc(), Duration.ofSeconds(5)));
        CountDownLatch ecrit = new CountDownLatch(1);
        CountDownLatch valider = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> optimisation = pool.submit(() -> transaction().execute(statut -> {
                verrouDesPlaces.verrouiller(CENTRE.valeur());
                jdbc().update("UPDATE patients SET salle_id = ?, position_id = ?, generateur_id = ?, jour_lundi = TRUE WHERE id = ?",
                        SALLE.valeur(), CRENEAU.valeur(), GENERATEUR.valeur(), ALICE.valeur());
                ecrit.countDown();
                attendre(valider);
                return null;
            }));
            assertThat(ecrit.await(20, TimeUnit.SECONDS)).isTrue();

            Future<String> fiche = pool.submit(() -> {
                try {
                    placer.placer(CENTRE.valeur(), BORIS.valeur(), SALLE.valeur(), CRENEAU.valeur(), GENERATEUR.valeur(),
                            Set.of(DayOfWeek.MONDAY));
                    return "ACCEPTE";
                } catch (PlacementDesPatients.Refus refus) {
                    return refus.code();
                }
            });
            Thread.sleep(300);
            assertThat(fiche.isDone()).as("la fiche attend le verrou tenu par l'optimisation").isFalse();

            valider.countDown();
            optimisation.get(20, TimeUnit.SECONDS);
            assertThat(fiche.get(20, TimeUnit.SECONDS)).isEqualTo("PLACEMENT_GENERATEUR_OCCUPE");
            assertThat(places().charger(CENTRE, RIEN, aujourdhui()).occupations().stream()
                    .filter(o -> GENERATEUR.equals(o.generateur())).count()).isOne();
        } finally {
            valider.countDown();
            pool.shutdownNow();
        }
    }

    /** Alertes sans effet : ces épreuves ne regardent que le verrou. */
    private static final class AlertesMuettes implements com.hemodialyse.backend.contexts.planification.application.NotificationsDuPlacement {
        @Override
        public boolean patientReplaceEnIsolement(com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId centre,
                                                 PatientId patient, String nomDuPatient, String salle, LocalDate jour) {
            return true;
        }

        @Override
        public boolean isolementImpossible(com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId centre,
                                           PatientId patient, String nomDuPatient, LocalDate jour) {
            return true;
        }

        @Override
        public boolean retourEnSalleOrdinairePropose(com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId centre,
                                                     PatientId patient, String nomDuPatient, String salleProposee, LocalDate jour) {
            return true;
        }

        @Override
        public boolean generateurIndisponible(com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId centre,
                                              com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId generateur,
                                              String code, String statut, int nombreDePatients, List<String> patientsCites,
                                              LocalDate jour) {
            return true;
        }
    }

    private static void attendre(CountDownLatch verrou) {
        try {
            verrou.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
