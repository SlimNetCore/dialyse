package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.AffecterAuRoulement;
import com.hemodialyse.backend.contexts.planification.application.AffecterUnRemplacant;
import com.hemodialyse.backend.contexts.planification.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.planification.domain.AbsenceInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.IdentiteDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Matricule;
import com.hemodialyse.backend.contexts.planification.domain.PeriodeDAbsence;
import com.hemodialyse.backend.contexts.planification.domain.Qualification;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.RemplacementsRepository;
import com.hemodialyse.backend.contexts.planification.domain.RoulementsRepository;
import com.hemodialyse.backend.contexts.planification.domain.TypeAbsence;
import com.hemodialyse.backend.contexts.planification.application.SallesDuCentre;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacycentres.HorlogeDesCentresLegacy;
import com.hemodialyse.backend.contexts.planification.infrastructure.motdepasse.NouveauxIdentifiantsDuPlanningAleatoires;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.AbsencesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.InfirmiersJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.RemplacementsJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.RoulementEtAbsencesLusParLeLegacyJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.RoulementsJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.RoulementEtAbsencesDesInfirmiers;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du roulement, des absences et des remplacements contre les adaptateurs JDBC (H2 et PostgreSQL 16 partagent ces tests) : les
 * dépôts passent le contrat des doubles, plus ce qui n'a de sens que sur une vraie base : lecture publiée, fuseau du centre et les
 * COURSES de la slice (deux affectations chevauchantes, un même remplaçant sur deux salles : une seule passe, jamais deux lignes).
 */
public abstract class PlanningDesInfirmiersJdbcContrat extends PlanningDesInfirmiersContrat {

    protected abstract JdbcTemplate jdbc();

    protected abstract TransactionTemplate transaction();

    /**
     * Pose les lignes des deux centres de test.
     */
    protected abstract void poserLesCentres();

    @Override
    protected RoulementsRepository roulements() {
        return new RoulementsJdbc(jdbc());
    }

    @Override
    protected AbsencesRepository absences() {
        return new AbsencesJdbc(jdbc());
    }

    @Override
    protected RemplacementsRepository remplacements() {
        return new RemplacementsJdbc(jdbc());
    }

    private InfirmiersRepository infirmiers() {
        return new InfirmiersJdbc(jdbc());
    }

    private Infirmier uneFiche(String matricule, boolean habilite) {
        Infirmier fiche = Infirmier.enregistrer(new InfirmierId(UUID.randomUUID()), CENTRE, Matricule.depuisLaSaisie(matricule),
                new IdentiteDeLInfirmier("Amrani", "Sara", null), Qualification.INFIRMIER, habilite);
        infirmiers().enregistrer(fiche);
        return fiche;
    }

    // ------------------------------------------------------------------ lecture publiée et fuseau

    @Test
    void should_publish_the_rotation_of_active_nurses_only_and_the_absences_and_replacements_of_a_period() {
        Infirmier active = uneFiche("P3-A", false);
        Infirmier inactive = uneFiche("P3-I", false);
        infirmiers().enregistrer(inactive.desactiver());
        SalleId salle = new SalleId(UUID.randomUUID());
        CreneauId matin = new CreneauId(UUID.randomUUID());
        poser(active.id(), new com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId(UUID.randomUUID()), salle,
                matin, EnumSet.of(JourDeLaSemaine.MARDI, JourDeLaSemaine.DIMANCHE));
        poser(inactive.id(), new com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId(UUID.randomUUID()),
                salle, matin, EnumSet.of(JourDeLaSemaine.MARDI));
        absences().enregistrer(AbsenceInfirmier.declarer(new AbsenceId(UUID.randomUUID()), CENTRE, active.id(),
                new PeriodeDAbsence(JOUR, JOUR.plusDays(3)), TypeAbsence.FORMATION, null, null));
        remplacements().enregistrer(com.hemodialyse.backend.contexts.planification.domain.Remplacement.reconstituer(
                new com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId(UUID.randomUUID()), CENTRE,
                JOUR.plusDays(1), salle, matin, active.id(), null));
        RoulementEtAbsencesDesInfirmiers publie = new RoulementEtAbsencesLusParLeLegacyJdbc(jdbc());

        var affectations = publie.affectationsDesInfirmiersActifs(CENTRE.valeur());
        var absencesPubliees = publie.absencesSurLaPeriode(CENTRE.valeur(), JOUR.plusDays(3), JOUR.plusDays(9));
        var remplacementsPublies = publie.remplacementsSurLaPeriode(CENTRE.valeur(), JOUR, JOUR.plusDays(1));

        assertThat(affectations).hasSize(1);
        assertThat(affectations.getFirst().infirmier()).isEqualTo(active.id().valeur());
        assertThat(affectations.getFirst().jours()).containsExactlyInAnyOrder(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY);
        assertThat(absencesPubliees).hasSize(1);
        assertThat(absencesPubliees.getFirst().type()).isEqualTo("FORMATION");
        assertThat(publie.absencesSurLaPeriode(CENTRE.valeur(), JOUR.plusDays(4), JOUR.plusDays(9))).isEmpty();
        assertThat(remplacementsPublies).hasSize(1);
        assertThat(publie.remplacementsSurLaPeriode(CENTRE.valeur(), JOUR.plusDays(2), JOUR.plusDays(9))).isEmpty();
        assertThat(publie.affectationsDesInfirmiersActifs(AUTRE.valeur())).isEmpty();
    }

    @Test
    void should_judge_today_at_the_centre_timezone_with_algiers_by_default_and_utc_when_configured() {
        Clock minuitMoinsLeMoinsDixUtc = Clock.fixed(Instant.parse("2026-11-02T23:30:00Z"), ZoneOffset.UTC);
        HorlogeDesCentresLegacy horloge = new HorlogeDesCentresLegacy(jdbc(), minuitMoinsLeMoinsDixUtc, ZoneId.of("Africa/Algiers"));

        assertThat(horloge.aujourdhui(CENTRE)).isEqualTo(LocalDate.of(2026, 11, 3));
        jdbc().update("UPDATE centers SET fuseau_horaire = 'UTC' WHERE id = ?", CENTRE.valeur());
        assertThat(horloge.aujourdhui(CENTRE)).isEqualTo(LocalDate.of(2026, 11, 2));
        jdbc().update("UPDATE centers SET fuseau_horaire = 'Pas/Une zone' WHERE id = ?", CENTRE.valeur());
        assertThat(horloge.aujourdhui(CENTRE)).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(horloge.aujourdhui(new CentreId(UUID.randomUUID()))).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    // ------------------------------------------------------------------ courses

    private UniteDeTravail unite() {
        return new UniteDeTravailSpring(transaction(), jdbc(), Duration.ofSeconds(5));
    }

    private static SallesDuCentre sallesConnues(Set<SalleId> salles) {
        return new SallesDuCentre() {
            @Override
            public Set<SalleId> toutes(CentreId centre) {
                return salles;
            }

            @Override
            public Set<SalleId> sallesDIsolement(CentreId centre) {
                return Set.of();
            }

            @Override
            public void marquerIsolement(CentreId centre, Set<SalleId> sallesDIsolement) {
            }
        };
    }

    /**
     * Lance les tâches ensemble ; rend, pour chacune, null (passée) ou l'exception levée.
     */
    private static List<Throwable> courir(List<Callable<Object>> taches) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(taches.size());
        CountDownLatch depart = new CountDownLatch(1);
        try {
            List<Future<Throwable>> futurs = new ArrayList<>();
            for (Callable<Object> tache : taches) {
                futurs.add(pool.submit(() -> {
                    depart.await();
                    try {
                        tache.call();
                        return null;
                    } catch (Throwable e) {
                        return e;
                    }
                }));
            }
            depart.countDown();
            List<Throwable> resultats = new ArrayList<>();
            for (Future<Throwable> futur : futurs) {
                try {
                    resultats.add(futur.get(60, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    resultats.add(e.getCause());
                }
            }
            return resultats;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void should_let_only_one_of_several_simultaneous_overlapping_assignments_of_a_nurse_through() throws Exception {
        Infirmier sara = uneFiche("P3-RACE", false);
        List<SalleId> salles = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            salles.add(new SalleId(UUID.randomUUID()));
        }
        CreneauId matin = new CreneauId(UUID.randomUUID());
        AffecterAuRoulement affecter = new AffecterAuRoulement(unite(), infirmiers(), roulements(), sallesConnues(Set.copyOf(salles)),
                (centre, creneau) -> true, new NouveauxIdentifiantsDuPlanningAleatoires(), (c, s, k, j, a) -> List.of(), centre -> JOUR);
        List<Callable<Object>> taches = new ArrayList<>();
        for (SalleId salle : salles) {
            taches.add(() -> affecter.executer(CENTRE, sara.id(), salle, matin, EnumSet.of(JourDeLaSemaine.LUNDI, JourDeLaSemaine.MARDI)));
        }

        List<Throwable> resultats = courir(taches);

        assertThat(resultats.stream().filter(r -> r == null)).hasSize(1);
        assertThat(resultats.stream().filter(r -> r != null)).allSatisfy(r -> assertThat(r).satisfiesAnyOf(
                e -> assertThat(e).isInstanceOf(EcritureConcurrente.class),
                e -> assertThat(((RegleDePlanificationViolee) e).code()).isEqualTo("AFFECTATION_CHEVAUCHEMENT")));
        assertThat(roulements().de(CENTRE, sara.id()).affectations()).hasSize(1);
    }

    @Test
    void should_let_only_one_of_several_simultaneous_replacements_of_the_same_nurse_on_a_slot_through() throws Exception {
        Infirmier renfort = uneFiche("P3-RENFORT", false);
        List<SalleId> salles = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            salles.add(new SalleId(UUID.randomUUID()));
        }
        CreneauId matin = new CreneauId(UUID.randomUUID());
        AffecterUnRemplacant remplacer = new AffecterUnRemplacant(unite(), infirmiers(), roulements(), absences(), remplacements(),
                sallesConnues(Set.copyOf(salles)), (centre, creneau) -> true, (centre, jour) -> true, centre -> JOUR.minusDays(1),
                new NouveauxIdentifiantsDuPlanningAleatoires());
        List<Callable<Object>> taches = new ArrayList<>();
        for (SalleId salle : salles) {
            taches.add(() -> remplacer.executer(CENTRE, JOUR, salle, matin, renfort.id(), null));
        }

        List<Throwable> resultats = courir(taches);

        assertThat(resultats.stream().filter(r -> r == null)).hasSize(1);
        assertThat(resultats.stream().filter(r -> r != null)).allSatisfy(r -> assertThat(r).satisfiesAnyOf(
                e -> assertThat(e).isInstanceOf(EcritureConcurrente.class),
                e -> assertThat(((RegleDePlanificationViolee) e).code()).isEqualTo("REMPLACEMENT_INELIGIBLE")));
        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM infirmier_remplacement WHERE center_id = ? AND infirmier_id = ?",
                Integer.class, CENTRE.valeur(), renfort.id().valeur())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ écritures directes de l'optimisation (P8, legacy)

    /**
     * Écriture directe d'une affectation comme le fait l'optimisation (P8) : SQL sur la table, sans passer par le contexte.
     */
    private void ecritureDeLOptimisation(InfirmierId infirmier, SalleId salle, CreneauId creneau, String jours) {
        jdbc().update("INSERT INTO infirmier_affectation (id, center_id, infirmier_id, salle_id, creneau_id, jours) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), CENTRE.valeur(), infirmier.valeur(), salle.valeur(), creneau.valeur(), jours);
    }

    @Test
    void should_not_lose_an_assignment_written_directly_by_the_optimisation_between_the_read_and_the_write_of_a_nurse_assignment()
            throws Exception {
        Infirmier sara = uneFiche("P3-P8-SANS-VERROU", false);
        SalleId salle = new SalleId(UUID.randomUUID());
        CreneauId matin = new CreneauId(UUID.randomUUID());
        AffecterAuRoulement affecter = new AffecterAuRoulement(unite(), infirmiers(), roulements(), sallesConnues(Set.of(salle)),
                (centre, creneau) -> true, new NouveauxIdentifiantsDuPlanningAleatoires(), (c, s, k, j, a) -> List.of(), centre -> JOUR);
        // lecture du roulement par P3, puis écriture de P8, puis écriture de P3 : la ligne de P8 doit survivre
        RoulementsJdbc depot = new RoulementsJdbc(jdbc());
        depot.de(CENTRE, sara.id());
        ecritureDeLOptimisation(sara.id(), salle, new CreneauId(UUID.randomUUID()), "VENDREDI");
        affecter.executer(CENTRE, sara.id(), salle, matin, EnumSet.of(JourDeLaSemaine.LUNDI));

        assertThat(roulements().de(CENTRE, sara.id()).affectations()).hasSize(2);
    }

    @Test
    void should_make_the_optimisation_wait_for_an_assignment_in_progress_when_it_takes_the_nurse_record_lock_first() throws Exception {
        Infirmier sara = uneFiche("P3-P8-AVEC-VERROU", false);
        SalleId salle = new SalleId(UUID.randomUUID());
        CreneauId matin = new CreneauId(UUID.randomUUID());
        CountDownLatch verrouille = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // P3 tient la fiche pendant son écriture
            Future<?> p3 = pool.submit(() -> transaction().execute(statut -> {
                infirmiers().pourEcriture(CENTRE, sara.id());
                verrouille.countDown();
                try {
                    liberer.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertThat(verrouille.await(20, TimeUnit.SECONDS)).isTrue();
            // P8 : verrou des fiches (port publié) puis écriture directe, dans sa transaction
            var verrouiller = new com.hemodialyse.backend.contexts.planification.application.VerrouillerLesFichesDesInfirmiers(
                    new UniteDeTravailSpring(transaction(), jdbc(), Duration.ofSeconds(20)), infirmiers());
            Future<?> p8 = pool.submit(() -> transaction().execute(statut -> {
                verrouiller.executer(CENTRE, List.of(sara.id()));
                ecritureDeLOptimisation(sara.id(), salle, matin, "LUNDI");
                return null;
            }));
            org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.TimeoutException.class, () -> p8.get(700, TimeUnit.MILLISECONDS));
            assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM infirmier_affectation WHERE infirmier_id = ?", Integer.class,
                    sara.id().valeur())).isZero();
            liberer.countDown();
            p3.get(20, TimeUnit.SECONDS);
            p8.get(20, TimeUnit.SECONDS);
            assertThat(roulements().de(CENTRE, sara.id()).affectations()).hasSize(1);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }
}
