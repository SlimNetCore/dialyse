package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.application.AnnulerUneAbsence;
import com.hemodialyse.backend.contexts.absencespatients.application.DeclarerUneAbsence;
import com.hemodialyse.backend.contexts.absencespatients.application.GenerateurIdentifiants;
import com.hemodialyse.backend.contexts.absencespatients.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.absencespatients.application.Intervenant;
import com.hemodialyse.backend.contexts.absencespatients.application.RattraperUneAbsence;
import com.hemodialyse.backend.contexts.absencespatients.application.SeancesRealisees;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencePatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.domain.DroitDeCorrection;
import com.hemodialyse.backend.contexts.absencespatients.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.absencespatients.domain.FaitsDuRattrapage;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.PeriodesFacturees;
import com.hemodialyse.backend.contexts.absencespatients.domain.RegleDAbsenceViolee;
import com.hemodialyse.backend.contexts.absencespatients.domain.SeanceRealisee;
import com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.AbsencesJdbc;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.transaction.UniteDeTravailSpring;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat de concurrence des écritures d'absences, sur de vraies transactions (H2 et PostgreSQL 16) : une écriture sur une absence
 * attend le verrou de l'écriture en cours puis voit son état validé (jamais « la dernière écriture gagne »), un rattrapage attend
 * les autres absences du patient, une déclaration du même jour perd en 409. Déterministe : l'écriture en cours est suspendue par un
 * verrou de test, jamais par un {@code sleep} de course.
 */
abstract class EcrituresConcurrentesContrat extends AbsencesContrat {

    private static final Intervenant SANS_DROIT = new Intervenant(SECRETAIRE, DroitDeCorrection.REFUSE);
    private static final Intervenant AVEC_DROIT = new Intervenant(MEDECIN, DroitDeCorrection.ACCORDE);
    private static final PeriodesFacturees AUCUNE_FACTURE = (centre, patient, jour) -> false;
    private static final com.hemodialyse.backend.contexts.absencespatients.application.GardeDesMoisDAbsences GARDE_OUVERTE =
            new com.hemodialyse.backend.contexts.absencespatients.application.GardeDesMoisDAbsences(
                    new com.hemodialyse.backend.contexts.absencespatients.doubles.MoisAbsencesEnMemoire());
    private final ExecutorService pool = Executors.newFixedThreadPool(3);

    protected abstract JdbcTemplate jdbc();

    protected abstract DataSource dataSource();

    @AfterEach
    void arreterLePool() {
        pool.shutdownNow();
    }

    private UniteDeTravailSpring unite() {
        return new UniteDeTravailSpring(new TransactionTemplate(new DataSourceTransactionManager(dataSource())));
    }

    private SeancesRealisees toutesRealisees() {
        return new SeancesRealisees() {
            @Override
            public List<SeanceRealisee> duCentre(CentreId centre, LocalDate du, LocalDate au) {
                return List.of();
            }

            @Override
            public List<LocalDate> joursDuPatient(CentreId centre, PatientId patient, LocalDate du, LocalDate au) {
                return List.of();
            }

            @Override
            public boolean realisee(CentreId centre, PatientId patient, LocalDate jour) {
                return true;
            }
        };
    }

    private Clock horloge() {
        return Clock.fixed(Instant.parse("2026-10-22T08:00:00Z"), ZoneOffset.UTC);
    }

    private void attendreEtVerifierQueLEcritureEstBloquee(Future<?> ecriture) throws Exception {
        Thread.sleep(500);
        assertThat(ecriture.isDone()).as("l'écriture attend le verrou de l'écriture en cours").isFalse();
    }

    private static Throwable cause(Future<?> futur) throws Exception {
        try {
            futur.get(30, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    @Test
    void une_annulation_attend_celle_en_cours_sur_la_meme_absence_puis_la_voit_annulee() throws Exception {
        AbsencesRepository absences = absences();
        absences.creer(aQualifier(1, CENTRE, AMRANI, JOUR));
        CountDownLatch verrouillee = new CountDownLatch(1);
        CountDownLatch liberation = new CountDownLatch(1);
        Future<?> premiere = pool.submit(() -> unite().executer(() -> {
            AbsencePatient a = absences.verrouiller(CENTRE, id(1)).orElseThrow();
            verrouillee.countDown();
            await(liberation);
            a.annuler("premier", SECRETAIRE, DroitDeCorrection.REFUSE, false, AUCUNE_FACTURE, Instant.now());
            absences.enregistrer(a);
            return null;
        }));
        assertThat(verrouillee.await(30, TimeUnit.SECONDS)).isTrue();
        AnnulerUneAbsence annuler = new AnnulerUneAbsence(unite(), absences, AUCUNE_FACTURE, (centre, absence) -> false,
                (c, p, j) -> {
                }, HorlogeDesCentres.enUtc(horloge()), GARDE_OUVERTE);
        Future<?> seconde = pool.submit(() -> annuler.executer(CENTRE, id(1), "second", AVEC_DROIT));

        attendreEtVerifierQueLEcritureEstBloquee(seconde);
        liberation.countDown();
        premiere.get(30, TimeUnit.SECONDS);

        assertThat(cause(seconde)).isInstanceOfSatisfying(RegleDAbsenceViolee.class,
                e -> assertThat(e.code()).isEqualTo("ABSENCE_DEJA_ANNULEE"));
        assertThat(absences.verrouiller(CENTRE, id(1)).orElseThrow().commentaire()).isEqualTo("premier");
    }

    @Test
    void deux_absences_du_meme_patient_ne_se_rattrapent_pas_par_la_meme_seance() throws Exception {
        AbsencesRepository absences = absences();
        absences.creer(aQualifier(1, CENTRE, AMRANI, JOUR));
        absences.creer(aQualifier(2, CENTRE, AMRANI, JOUR.plusDays(1)));
        LocalDate rattrapage = JOUR.plusDays(3);
        CountDownLatch verrouillee = new CountDownLatch(1);
        CountDownLatch liberation = new CountDownLatch(1);
        Future<?> premiere = pool.submit(() -> unite().executer(() -> {
            AbsencesRepository.AbsenceEtRattrapages lue = absences.verrouillerPourRattrapage(CENTRE, id(1)).orElseThrow();
            verrouillee.countDown();
            await(liberation);
            lue.absence().rattraper(rattrapage, MEDECIN, DroitDeCorrection.ACCORDE, AUCUNE_FACTURE, new FaitsDuRattrapage() {
                @Override
                public boolean seanceRealisee(LocalDate jour) {
                    return true;
                }

                @Override
                public boolean dejaUtilisee(LocalDate jour) {
                    return lue.datesDeRattrapageUtilisees().contains(jour);
                }
            }, LocalDate.of(2026, 10, 22), Instant.now());
            absences.enregistrer(lue.absence());
            return null;
        }));
        assertThat(verrouillee.await(30, TimeUnit.SECONDS)).isTrue();
        RattraperUneAbsence rattraper = new RattraperUneAbsence(unite(), absences, AUCUNE_FACTURE, toutesRealisees(),
                (c, p, j) -> {
                }, HorlogeDesCentres.enUtc(horloge()), GARDE_OUVERTE);
        Future<?> seconde = pool.submit(() -> rattraper.executer(CENTRE, id(2), rattrapage, AVEC_DROIT));

        attendreEtVerifierQueLEcritureEstBloquee(seconde);
        liberation.countDown();
        premiere.get(30, TimeUnit.SECONDS);

        assertThat(cause(seconde)).isInstanceOfSatisfying(RegleDAbsenceViolee.class,
                e -> assertThat(e.code()).isEqualTo("ABSENCE_RATTRAPAGE_DEJA_UTILISEE"));
        assertThat(absences.verrouiller(CENTRE, id(1)).orElseThrow().statut()).isEqualTo(StatutAbsence.RATTRAPEE);
        assertThat(absences.verrouiller(CENTRE, id(2)).orElseThrow().statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
    }

    @Test
    void la_declaration_du_meme_jour_perd_en_ecriture_concurrente_quand_une_autre_vient_de_la_creer() throws Exception {
        AbsencesRepository absences = absences();
        CountDownLatch inseree = new CountDownLatch(1);
        CountDownLatch liberation = new CountDownLatch(1);
        Future<?> premiere = pool.submit(() -> unite().executer(() -> {
            absences.creer(aQualifier(1, CENTRE, AMRANI, JOUR));
            inseree.countDown();
            await(liberation);
            return null;
        }));
        assertThat(inseree.await(30, TimeUnit.SECONDS)).isTrue();
        GenerateurIdentifiants ids = () -> new AbsenceId(new UUID(0x8004, 2));
        DeclarerUneAbsence declarer = new DeclarerUneAbsence(unite(), (centre, patient) -> true, absences, AUCUNE_FACTURE,
                toutesRealiseesSauCeJour(), (centre, patient, jour) -> Optional.empty(), (centre, jour) -> java.math.BigDecimal.ZERO,
                ids, (c, p, j) -> {
        }, HorlogeDesCentres.enUtc(Clock.fixed(Instant.parse("2026-10-12T08:00:00Z"), ZoneOffset.UTC)),
                GARDE_OUVERTE);
        Future<?> perdante = pool.submit(() -> declarer.executer(CENTRE,
                new DeclarerUneAbsence.Commande(AMRANI, JOUR, null, null), SANS_DROIT));

        Thread.sleep(300);
        liberation.countDown();
        premiere.get(30, TimeUnit.SECONDS);

        assertThat(cause(perdante)).isInstanceOf(EcritureConcurrente.class);
        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM absence_patient WHERE center_id = ? AND patient_id = ? "
                + "AND date_seance = ?", Integer.class, CENTRE.valeur(), AMRANI.valeur(), java.sql.Date.valueOf(JOUR))).isEqualTo(1);
    }

    private SeancesRealisees toutesRealiseesSauCeJour() {
        return new SeancesRealisees() {
            @Override
            public List<SeanceRealisee> duCentre(CentreId centre, LocalDate du, LocalDate au) {
                return List.of();
            }

            @Override
            public List<LocalDate> joursDuPatient(CentreId centre, PatientId patient, LocalDate du, LocalDate au) {
                return List.of();
            }

            @Override
            public boolean realisee(CentreId centre, PatientId patient, LocalDate jour) {
                return false;
            }
        };
    }

    @Test
    void une_ecriture_concurrente_sur_une_absence_supprimee_entre_temps_est_une_ecriture_concurrente() {
        AbsencesRepository absences = absences();
        assertThatThrownBy(() -> absences.enregistrer(aQualifier(7, CENTRE, AMRANI, JOUR))).isInstanceOf(EcritureConcurrente.class);
    }

    private static void await(CountDownLatch verrou) {
        try {
            verrou.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
