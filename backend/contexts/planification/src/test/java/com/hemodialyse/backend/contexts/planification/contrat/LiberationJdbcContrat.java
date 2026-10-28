package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.LibererLaPlaceDeLaFiche;
import com.hemodialyse.backend.contexts.planification.application.LibererLesPlacesEchues;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CachesDesPatients;
import com.hemodialyse.backend.contexts.planification.application.CentresActifs;
import com.hemodialyse.backend.contexts.planification.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.planification.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.PlaceTenue;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient.MouvementsDesPatientsJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient.PlacesTenuesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.VerrouDesPlacesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.transaction.UniteDeTravailSpring;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de la libération des places contre les tables réelles ({@code patients}, {@code mouvement_patient}) : lecture des places
 * tenues avec la règle de sortie du legacy (asymétrie du jour d'évènement conservée), écriture limitée aux colonnes de place,
 * mouvement {@code PLACE_LIBEREE} sans doublon, transaction par patient, lecture sous verrou. Exécuté sur H2 et sur PostgreSQL 16.
 * Le fake en mémoire est prouvé par {@code LiberationDesPlacesApplicationTest} (mêmes règles, mêmes noms de comportements).
 */
abstract class LiberationJdbcContrat {

    static final CentreId CENTRE = new CentreId(UUID.fromString("c7000000-0000-0000-0000-0000000000a1"));
    static final CentreId AUTRE = new CentreId(UUID.fromString("c7000000-0000-0000-0000-0000000000b2"));
    static final LocalDate J = LocalDate.of(2026, 10, 27);
    static final UUID SALLE = UUID.fromString("5a000000-0000-0000-0000-000000000001");
    static final UUID CRENEAU = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    static final UUID GENERATEUR = UUID.fromString("6e000000-0000-0000-0000-000000000001");

    protected static UUID patient(int n) {
        return new UUID(0xc7L, n);
    }

    protected abstract JdbcTemplate jdbc();

    // ------------------------------------------------------------------ fixtures

    /**
     * Délai d'attente d'un verrou de ligne pour les tests de course (PostgreSQL : SET LOCAL lock_timeout ; H2 : son délai propre).
     */
    protected Duration delaiDeVerrou() {
        return Duration.ofMillis(800);
    }

    /**
     * Patient du centre avec une place complète (salle, créneau, générateur, lundi et mercredi).
     */
    protected void fiche(CentreId centre, int n, String etat, LocalDate evenement) {
        jdbc().update("INSERT INTO patients (id, center_id, nom, prenom, sexe, type_patient, numero_assurance, date_admission, etat_patient, "
                        + "date_evenement_etat, salle_id, position_id, generateur_id, jour_lundi, jour_mercredi) "
                        + "VALUES (?,?,?,'X','M','NON_VACANCIER',?,?,?,?,?,?,?,TRUE,TRUE)",
                patient(n), centre.valeur(), "P" + n, "NA" + n, Date.valueOf(J.minusYears(1)), etat,
                evenement == null ? null : Date.valueOf(evenement), SALLE, CRENEAU, GENERATEUR);
    }

    protected boolean aUnePlace(int n) {
        return jdbc().queryForObject("SELECT COUNT(*) FROM patients WHERE id = ? AND (salle_id IS NOT NULL OR position_id IS NOT NULL "
                + "OR generateur_id IS NOT NULL)", Integer.class, patient(n)) > 0;
    }

    protected int mouvements() {
        return jdbc().queryForObject("SELECT COUNT(*) FROM mouvement_patient WHERE type = 'PLACE_LIBEREE' AND center_id IN (?, ?)",
                Integer.class, CENTRE.valeur(), AUTRE.valeur());
    }

    protected PlacesTenuesJdbc places() {
        return new PlacesTenuesJdbc(jdbc());
    }

    protected MouvementsDesPatientsJdbc traces() {
        return new MouvementsDesPatientsJdbc(jdbc());
    }

    private PlaceTenue.PlaceLiberee liberee(int n) {
        return places().de(CENTRE, new PatientId(patient(n))).orElseThrow().liberer(J, J, Instant.now()).orElseThrow();
    }

    // ------------------------------------------------------------------ places tenues

    @Test
    void should_list_only_the_patients_of_the_centre_who_still_hold_a_place() {
        fiche(CENTRE, 1, "PERMANENT", null);
        fiche(AUTRE, 2, "PERMANENT", null);
        jdbc().update("INSERT INTO patients (id, center_id, nom, prenom, sexe, type_patient, numero_assurance, date_admission, etat_patient) "
                        + "VALUES (?,?,?,'X','M','NON_VACANCIER',?,?,?)",
                patient(3), CENTRE.valeur(), "P3", "NA3", Date.valueOf(J.minusYears(1)), "PERMANENT");
        assertThat(places().duCentre(CENTRE)).extracting(p -> p.patient().valeur()).containsExactly(patient(1));
    }

    @Test
    void should_keep_the_asymmetry_of_the_day_of_the_event_of_the_legacy() {
        LocalDate d = J.minusDays(1);
        fiche(CENTRE, 1, "TRANSFERE", d);
        fiche(CENTRE, 2, "GUERRI", d);
        fiche(CENTRE, 3, "DECEDE", d);
        fiche(CENTRE, 4, "GREFFE", d);
        fiche(CENTRE, 5, "TRANSFERE", null);
        fiche(CENTRE, 6, "OCCASIONNEL", d);
        fiche(CENTRE, 7, "PERMANENT", d);
        fiche(CENTRE, 8, "SUSPENDU", d);
        fiche(CENTRE, 9, "transfere", d);
        fiche(CENTRE, 10, null, d);
        fiche(CENTRE, 11, "VACANCIER_LOCAL", null);
        List<Integer> echues = new ArrayList<>();
        for (PlaceTenue p : places().duCentre(CENTRE)) {
            if (p.estEchueLe(J)) echues.add((int) p.patient().valeur().getLeastSignificantBits());
        }
        // J = lendemain du jour de l'évènement : transféré et guéri libérés ; décédé et greffé libérés la veille déjà ; sortie sans date
        // libérée tout de suite ; séjour fini libéré ; permanent, état inconnu, minuscules, sans état, séjour sans fin : jamais
        assertThat(echues).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
        List<Integer> memeJour = new ArrayList<>();
        for (PlaceTenue p : places().duCentre(CENTRE)) {
            if (p.estEchueLe(J.minusDays(1))) memeJour.add((int) p.patient().valeur().getLeastSignificantBits());
        }
        assertThat(memeJour).as("le jour même de la date : seuls décédé, greffé et sortie sans date").containsExactlyInAnyOrder(3, 4, 5);
    }

    @Test
    void should_not_let_the_sleep_prevent_the_release_of_a_leaving_patient() {
        fiche(CENTRE, 1, "TRANSFERE", J.minusDays(3));
        jdbc().update("UPDATE patients SET en_sommeil = TRUE WHERE id = ?", patient(1));
        assertThat(places().de(CENTRE, new PatientId(patient(1))).orElseThrow().estEchueLe(J)).isTrue();
    }

    @Test
    void should_read_a_place_only_in_its_own_centre_and_under_lock() {
        fiche(CENTRE, 1, "PERMANENT", null);
        assertThat(places().de(AUTRE, new PatientId(patient(1)))).isEmpty();
        assertThat(places().verrouillee(CENTRE, new PatientId(patient(1)))).isPresent();
        assertThat(places().verrouillee(AUTRE, new PatientId(patient(1)))).isEmpty();
    }

    @Test
    void should_erase_only_the_columns_of_the_place() {
        fiche(CENTRE, 1, "TRANSFERE", J.minusDays(3));
        places().liberer(liberee(1));
        assertThat(aUnePlace(1)).isFalse();
        var ligne = jdbc().queryForMap("SELECT nom, etat_patient, en_sommeil, jour_lundi, jour_mercredi FROM patients WHERE id = ?", patient(1));
        assertThat(ligne.get("nom")).isEqualTo("P1");
        assertThat(ligne.get("etat_patient")).isEqualTo("TRANSFERE");
        assertThat(ligne.get("en_sommeil")).as("la ligne entière n'est jamais réécrite (KB-PLN-84)").isNull();
        assertThat(ligne.get("jour_lundi")).isEqualTo(false);
        assertThat(ligne.get("jour_mercredi")).isEqualTo(false);
    }

    @Test
    void should_not_touch_the_patient_of_another_centre() {
        fiche(AUTRE, 1, "TRANSFERE", J.minusDays(3));
        places().liberer(new PlaceTenue.PlaceLiberee(CENTRE, new PatientId(patient(1)), "TRANSFERE",
                new PlaceTenue.PlaceQuittee(null, null, null, null), J, true, Instant.now()));
        assertThat(aUnePlace(1)).isTrue();
    }

    // ------------------------------------------------------------------ mouvements

    @Test
    void should_trace_the_release_with_the_place_left_and_not_twice_in_a_row() {
        fiche(CENTRE, 1, "TRANSFERE", J.minusDays(3));
        var l = liberee(1);
        assertThat(traces().dejaTrace(l)).isFalse();
        traces().tracer(l);
        assertThat(traces().dejaTrace(l)).isTrue();
        var m = jdbc().queryForMap("SELECT type, automatique, date_effet, etat_precedent, etat_nouveau, salle_id, jours_dialyse "
                + "FROM mouvement_patient WHERE patient_id = ?", patient(1));
        assertThat(m.get("type")).isEqualTo("PLACE_LIBEREE");
        assertThat(m.get("automatique")).isEqualTo(true);
        assertThat(m.get("etat_precedent")).isEqualTo("TRANSFERE");
        assertThat(m.get("etat_nouveau")).isEqualTo("TRANSFERE");
        assertThat(m.get("jours_dialyse")).isEqualTo("LUNDI,MERCREDI");
        assertThat(m.get("date_effet").toString()).isEqualTo(J.toString());
    }

    @Test
    void should_trace_again_after_another_movement_of_the_patient() {
        fiche(CENTRE, 1, "TRANSFERE", J.minusDays(3));
        var l = liberee(1);
        traces().tracer(l);
        jdbc().update("INSERT INTO mouvement_patient (id, center_id, patient_id, type, date_effet, etat_precedent, etat_nouveau, "
                        + "jours_dialyse, automatique, cree_le) VALUES (?,?,?,'REPRISE',?,?,?,'',FALSE,?)", UUID.randomUUID(),
                CENTRE.valeur(), patient(1), Date.valueOf(J), "TRANSFERE", "PERMANENT", java.sql.Timestamp.from(Instant.now().plusSeconds(5)));
        assertThat(traces().dejaTrace(l)).isFalse();
    }

    // ------------------------------------------------------------------ cas d'usage sur la vraie base

    Monde monde() {
        JdbcTemplate jdbc = jdbc();
        UniteDeTravail unite = new UniteDeTravailSpring(new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())),
                jdbc, delaiDeVerrou());
        List<String> alertes = new ArrayList<>();
        List<PatientId> caches = new ArrayList<>();
        HorlogeDesCentres horloge = centre -> J;
        CentresActifs actifs = () -> List.of(CENTRE, AUTRE);
        CachesDesPatients cachesDesPatients = (centre, patient) -> caches.add(new PatientId(patient));
        Clock instant = Clock.fixed(Instant.parse("2026-10-27T03:00:00Z"), ZoneOffset.UTC);
        var verrou = new VerrouDesPlacesJdbc(jdbc);
        var nuit = new LibererLesPlacesEchues(actifs, horloge, unite, verrou, places(), traces(), cachesDesPatients,
                (centre, jour, nb) -> alertes.add(centre + "|" + jour + "|" + nb), instant);
        var fiche = new LibererLaPlaceDeLaFiche(horloge, unite, verrou, places(), traces(), cachesDesPatients, instant);
        return new Monde(nuit, fiche, alertes, caches);
    }

    @Test
    void should_free_each_centre_in_its_own_transaction_and_write_the_movement_with_the_release() {
        fiche(CENTRE, 1, "TRANSFERE", J.minusDays(2));
        fiche(AUTRE, 2, "GUERRI", J.minusDays(2));
        fiche(CENTRE, 3, "PERMANENT", null);
        Monde m = monde();
        m.nuit().liberer();
        assertThat(aUnePlace(1)).isFalse();
        assertThat(aUnePlace(2)).isFalse();
        assertThat(aUnePlace(3)).isTrue();
        assertThat(mouvements()).isEqualTo(2);
        assertThat(m.caches()).hasSize(2);
        m.nuit().liberer();
        assertThat(mouvements()).as("idempotent").isEqualTo(2);
    }

    @Test
    void should_free_the_others_and_signal_the_failed_patient_of_a_batch() {
        fiche(CENTRE, 1, "TRANSFERE", J.minusDays(2));
        fiche(CENTRE, 2, "TRANSFERE", J.minusDays(2));
        fiche(CENTRE, 3, "TRANSFERE", J.minusDays(2));
        jdbc().execute("ALTER TABLE mouvement_patient ADD CONSTRAINT zz_p7_echec CHECK (patient_id <> '" + patient(2) + "')");
        try {
            Monde m = monde();
            m.nuit().liberer();
            assertThat(aUnePlace(1)).isFalse();
            assertThat(aUnePlace(2)).as("sa transaction est annulée : ni mouvement ni place libérée").isTrue();
            assertThat(aUnePlace(3)).isFalse();
            assertThat(mouvements()).isEqualTo(2);
            assertThat(m.alertes()).singleElement().asString().endsWith("|1");
        } finally {
            jdbc().execute("ALTER TABLE mouvement_patient DROP CONSTRAINT zz_p7_echec");
        }
        monde().nuit().liberer();
        assertThat(aUnePlace(2)).as("la nuit suivante réessaie").isFalse();
        assertThat(mouvements()).isEqualTo(3);
    }

    /**
     * Écrasement inverse (revue P7) : une fiche enregistrée APRÈS la nuit avec l'ancienne place d'un patient sorti est refusée par le
     * contrôle de placement (PLACEMENT_PATIENT_SORTI) ; ici, le patient libéré par la nuit est bien vu « sorti » par ce contrôle.
     */
    @Test
    void should_see_as_left_the_patient_freed_by_the_night_so_that_a_late_record_cannot_give_the_place_back() {
        fiche(CENTRE, 1, "TRANSFERE", J.minusDays(2));
        monde().nuit().liberer(CENTRE);
        assertThat(aUnePlace(1)).isFalse();
        assertThat(new com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient.PlacesDuCentreJdbc(jdbc())
                .estSorti(CENTRE, new PatientId(patient(1)), J)).isTrue();
    }

    @Test
    void should_free_a_record_judged_the_day_before_and_keep_a_record_left_yesterday() {
        fiche(CENTRE, 1, "TRANSFERE", J.minusDays(2));
        fiche(CENTRE, 2, "TRANSFERE", J.minusDays(1));
        Monde m = monde();
        assertThat(m.fiche().siEchue(CENTRE.valeur(), patient(1))).isTrue();
        assertThat(m.fiche().siEchue(CENTRE.valeur(), patient(2))).isFalse();
        assertThat(aUnePlace(1)).isFalse();
        assertThat(aUnePlace(2)).isTrue();
    }

    @Test
    void should_report_a_patient_whose_centre_lock_is_held_by_another_write_and_free_it_the_next_night() throws Exception {
        fiche(CENTRE, 1, "TRANSFERE", J.minusDays(2));
        JdbcTemplate jdbc = jdbc();
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        // la ligne du centre existe (committée) : le verrou la crée sinon
        tx.executeWithoutResult(s -> new VerrouDesPlacesJdbc(jdbc).prendre(CENTRE));
        CountDownLatch tenu = new CountDownLatch(1);
        CountDownLatch relache = new CountDownLatch(1);
        CompletableFuture<Void> autreEcriture = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            jdbc.queryForList("SELECT center_id FROM planning_parametres WHERE center_id = ? FOR UPDATE", CENTRE.valeur());
            tenu.countDown();
            try {
                relache.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(tenu.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            Monde m = monde();
            var bilan = m.nuit().liberer(CENTRE);
            assertThat(bilan.incidents()).hasSize(1);
            assertThat(aUnePlace(1)).isTrue();
            assertThat(m.alertes()).hasSize(1);
        } finally {
            relache.countDown();
            autreEcriture.get(20, TimeUnit.SECONDS);
        }
        monde().nuit().liberer(CENTRE);
        assertThat(aUnePlace(1)).isFalse();
    }

    record Monde(LibererLesPlacesEchues nuit, LibererLaPlaceDeLaFiche fiche, List<String> alertes,
                 List<PatientId> caches) {
    }
}
