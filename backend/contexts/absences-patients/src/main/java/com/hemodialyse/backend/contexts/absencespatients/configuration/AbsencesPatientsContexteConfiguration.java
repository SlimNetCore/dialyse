package com.hemodialyse.backend.contexts.absencespatients.configuration;

import com.hemodialyse.backend.contexts.absencespatients.application.AnnulerUneAbsence;
import com.hemodialyse.backend.contexts.absencespatients.application.DeclarerUneAbsence;
import com.hemodialyse.backend.contexts.absencespatients.application.ExclusionsDeSeances;
import com.hemodialyse.backend.contexts.absencespatients.application.ForfaitsDesPatients;
import com.hemodialyse.backend.contexts.absencespatients.application.GenerateurIdentifiants;
import com.hemodialyse.backend.contexts.absencespatients.application.LectureDesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.ListerLesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.ListerLesSeancesDeRattrapage;
import com.hemodialyse.backend.contexts.absencespatients.application.NomsDesPatients;
import com.hemodialyse.backend.contexts.absencespatients.application.ObtenirLaSynthese;
import com.hemodialyse.backend.contexts.absencespatients.application.PatientsDuCentre;
import com.hemodialyse.backend.contexts.absencespatients.application.QualifierUneAbsence;
import com.hemodialyse.backend.contexts.absencespatients.application.RattraperUneAbsence;
import com.hemodialyse.backend.contexts.absencespatients.application.RetirerLAbsenceDeLExclusion;
import com.hemodialyse.backend.contexts.absencespatients.application.SeancesRealisees;
import com.hemodialyse.backend.contexts.absencespatients.application.SessionCourante;
import com.hemodialyse.backend.contexts.absencespatients.application.SuivreLaSemaine;
import com.hemodialyse.backend.contexts.absencespatients.application.TauxDeTva;
import com.hemodialyse.backend.contexts.absencespatients.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.domain.PeriodesFacturees;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.comptabilite.TauxDeTvaComptabilite;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.facturation.PeriodesFactureesDeLaFacturation;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacycouverture.ForfaitsDesPatientsJdbc;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacypatient.NomsDesPatientsJdbc;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacypatient.PatientsDuCentreJdbc;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyseance.ExclusionsDeSeancesJdbc;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyseance.SeancesRealiseesJdbc;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.identifiants.GenerateurDIdentifiantsAleatoires;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.AbsencesJdbc;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.LectureDesAbsencesJdbc;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.absencespatients.interfaces.facturation.ExclusionDeLaFacturation;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.SignalementDesSaisies;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ParametrageFiscal;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.DeclarationDesAbsences;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FacturationDuPatient;
import com.hemodialyse.backend.contexts.absencespatients.application.CentresActifs;
import com.hemodialyse.backend.contexts.absencespatients.application.ControlerLesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.DetecterUnePeriodePassee;
import com.hemodialyse.backend.contexts.absencespatients.application.DetectionAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.FuseauxDesCentres;
import com.hemodialyse.backend.contexts.absencespatients.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.absencespatients.application.NotificationDesAbsencesAQualifier;
import com.hemodialyse.backend.contexts.absencespatients.application.PatientsAttendus;
import com.hemodialyse.backend.contexts.absencespatients.application.ReconciliationAbsences;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisDAbsencesOuverts;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyorganisation.CentresActifsJdbc;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyplanning.PatientsAttendusJdbc;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.horloge.FuseauParDefaut;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.mois.TousLesMoisSontOuverts;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.NotificationsDuCentre;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

/**
 * Composition root du contexte Absences de patients : seul endroit qui connaît toutes les couches. Slice A1 : consultation du
 * suivi ; slice A2 : cycle de vie (déclaration, qualification, rattrapage, annulation, exclusion de la Facturation). Les noms
 * de beans sont préfixés {@code absencesPatients} (un seul contexte Spring avec le legacy et les autres contextes) ; l'horloge
 * du contexte est un {@code HorlogeDesCentres} (le jour courant est celui du fuseau du centre, décision 7, slice A3), pas un
 * bean {@code Clock}, pour ne pas concurrencer celle des autres contextes. Slice A3 : détection de nuit. Le signalement des saisies et les notifications sont fournis par la plateforme.
 */
@Configuration(proxyBeanMethods = false)
public class AbsencesPatientsContexteConfiguration {

    @Bean
    SessionCourante absencesPatientsSessionCourante() {
        return new SessionLegacy();
    }

    @Bean
    LectureDesAbsences absencesPatientsLectureDesAbsences(JdbcTemplate jdbc) {
        return new LectureDesAbsencesJdbc(jdbc);
    }

    @Bean
    SeancesRealisees absencesPatientsSeancesRealisees(JdbcTemplate jdbc) {
        return new SeancesRealiseesJdbc(jdbc);
    }

    @Bean
    NomsDesPatients absencesPatientsNomsDesPatients(JdbcTemplate jdbc) {
        return new NomsDesPatientsJdbc(jdbc);
    }

    @Bean
    ListerLesAbsences absencesPatientsListerLesAbsences(LectureDesAbsences lecture, NomsDesPatients noms,
                                                        HorlogeDesCentres horloge) {
        return new ListerLesAbsences(lecture, noms, horloge);
    }

    @Bean
    SuivreLaSemaine absencesPatientsSuivreLaSemaine(LectureDesAbsences lecture, SeancesRealisees seances,
                                                         HorlogeDesCentres horloge) {
        return new SuivreLaSemaine(lecture, seances, horloge);
    }

    @Bean
    ObtenirLaSynthese absencesPatientsObtenirLaSynthese(LectureDesAbsences lecture, HorlogeDesCentres horloge) {
        return new ObtenirLaSynthese(lecture, horloge);
    }

    @Bean
    ListerLesSeancesDeRattrapage absencesPatientsListerLesSeancesDeRattrapage(LectureDesAbsences lecture,
                                                                             SeancesRealisees seances, HorlogeDesCentres horloge) {
        return new ListerLesSeancesDeRattrapage(lecture, seances, horloge);
    }

    // Slice A2 : cycle de vie des absences (déclaration, qualification, rattrapage, annulation, retrait de l'exclusion).

    @Bean
    UniteDeTravail absencesPatientsUniteDeTravail(PlatformTransactionManager transactionManager, JdbcTemplate jdbc) {
        return new UniteDeTravailSpring(new TransactionTemplate(transactionManager), jdbc, Duration.ofSeconds(5));
    }

    @Bean
    AbsencesRepository absencesPatientsAbsences(JdbcTemplate jdbc) {
        return new AbsencesJdbc(jdbc);
    }

    @Bean
    PatientsDuCentre absencesPatientsPatientsDuCentre(JdbcTemplate jdbc) {
        return new PatientsDuCentreJdbc(jdbc);
    }

    @Bean
    ForfaitsDesPatients absencesPatientsForfaitsDesPatients(JdbcTemplate jdbc) {
        return new ForfaitsDesPatientsJdbc(jdbc);
    }

    @Bean
    TauxDeTva absencesPatientsTauxDeTva(ParametrageFiscal fiscal) {
        return new TauxDeTvaComptabilite(fiscal);
    }

    @Bean
    PeriodesFacturees absencesPatientsPeriodesFacturees(FacturationDuPatient facturation) {
        return new PeriodesFactureesDeLaFacturation(facturation);
    }

    @Bean
    ExclusionsDeSeances absencesPatientsExclusionsDeSeances(JdbcTemplate jdbc) {
        return new ExclusionsDeSeancesJdbc(jdbc);
    }

    @Bean
    GenerateurIdentifiants absencesPatientsGenerateurIdentifiants() {
        return new GenerateurDIdentifiantsAleatoires();
    }

    @Bean
    DeclarerUneAbsence absencesPatientsDeclarerUneAbsence(UniteDeTravail uniteDeTravail, PatientsDuCentre patients,
                                                         AbsencesRepository absences, PeriodesFacturees periodes,
                                                         SeancesRealisees seances, ForfaitsDesPatients forfaits,
                                                         TauxDeTva taux, GenerateurIdentifiants identifiants,
                                                         SignalementDesSaisies signalement, HorlogeDesCentres horloge) {
        return new DeclarerUneAbsence(uniteDeTravail, patients, absences, periodes, seances, forfaits, taux, identifiants,
                signalement, horloge);
    }

    @Bean
    QualifierUneAbsence absencesPatientsQualifierUneAbsence(UniteDeTravail uniteDeTravail, AbsencesRepository absences,
                                                           PeriodesFacturees periodes, SignalementDesSaisies signalement, HorlogeDesCentres horloge) {
        return new QualifierUneAbsence(uniteDeTravail, absences, periodes, signalement, horloge);
    }

    @Bean
    RattraperUneAbsence absencesPatientsRattraperUneAbsence(UniteDeTravail uniteDeTravail, AbsencesRepository absences,
                                                           PeriodesFacturees periodes, SeancesRealisees seances,
                                                           SignalementDesSaisies signalement, HorlogeDesCentres horloge) {
        return new RattraperUneAbsence(uniteDeTravail, absences, periodes, seances, signalement, horloge);
    }

    @Bean
    AnnulerUneAbsence absencesPatientsAnnulerUneAbsence(UniteDeTravail uniteDeTravail, AbsencesRepository absences,
                                                       PeriodesFacturees periodes, ExclusionsDeSeances exclusions,
                                                       SignalementDesSaisies signalement, HorlogeDesCentres horloge) {
        return new AnnulerUneAbsence(uniteDeTravail, absences, periodes, exclusions, signalement, horloge);
    }

    @Bean
    RetirerLAbsenceDeLExclusion absencesPatientsRetirerLAbsenceDeLExclusion(UniteDeTravail uniteDeTravail,
                                                                           AbsencesRepository absences) {
        return new RetirerLAbsenceDeLExclusion(uniteDeTravail, absences);
    }

    // Slice A3 : détection de nuit (réconciliation, détection, notification), fuseau du centre.

    @Bean
    FuseauxDesCentres absencesPatientsFuseauxDesCentres(
            @Value("${app.absences.fuseau-par-defaut:Africa/Algiers}") String fuseauParDefaut) {
        return new FuseauParDefaut(ZoneId.of(fuseauParDefaut));
    }

    @Bean
    HorlogeDesCentres absencesPatientsHorlogeDesCentres(FuseauxDesCentres fuseaux) {
        return new HorlogeDesCentres(Clock.systemUTC(), fuseaux);
    }

    @Bean
    MoisDAbsencesOuverts absencesPatientsMoisDAbsencesOuverts() {
        return new TousLesMoisSontOuverts();
    }

    @Bean
    CentresActifs absencesPatientsCentresActifs(JdbcTemplate jdbc) {
        return new CentresActifsJdbc(jdbc);
    }

    @Bean
    PatientsAttendus absencesPatientsPatientsAttendus(JdbcTemplate jdbc) {
        return new PatientsAttendusJdbc(jdbc);
    }

    @Bean
    ReconciliationAbsences absencesPatientsReconciliationAbsences(UniteDeTravail uniteDeTravail, AbsencesRepository absences,
                                                                 SeancesRealisees seances, PeriodesFacturees periodes,
                                                                 MoisDAbsencesOuverts mois) {
        return new ReconciliationAbsences(uniteDeTravail, absences, seances, periodes, mois);
    }

    @Bean
    DetectionAbsences absencesPatientsDetectionAbsences(UniteDeTravail uniteDeTravail, PatientsAttendus attendus,
                                                       AbsencesRepository absences, PeriodesFacturees periodes,
                                                       MoisDAbsencesOuverts mois, ForfaitsDesPatients forfaits, TauxDeTva taux,
                                                       GenerateurIdentifiants identifiants) {
        return new DetectionAbsences(uniteDeTravail, attendus, absences, periodes, mois, forfaits, taux, identifiants);
    }

    @Bean
    NotificationDesAbsencesAQualifier absencesPatientsNotificationDesAbsencesAQualifier(LectureDesAbsences lecture,
                                                                                      MoisDAbsencesOuverts mois,
                                                                                      NotificationsDuCentre notifications) {
        return new NotificationDesAbsencesAQualifier(lecture, mois, notifications);
    }

    @Bean
    ControlerLesAbsences absencesPatientsControlerLesAbsences(CentresActifs centres, HorlogeDesCentres horloge,
                                                             ReconciliationAbsences reconciliation, DetectionAbsences detection,
                                                             NotificationDesAbsencesAQualifier notification) {
        return new ControlerLesAbsences(centres, horloge, reconciliation, detection, notification);
    }

    @Bean
    DetecterUnePeriodePassee absencesPatientsDetecterUnePeriodePassee(HorlogeDesCentres horloge,
                                                                     ReconciliationAbsences reconciliation,
                                                                     DetectionAbsences detection) {
        return new DetecterUnePeriodePassee(horloge, reconciliation, detection);
    }

    /**
     * Le langage publié que la Facturation attend des Absences pour exclure et réinclure une séance (ADR 0031).
     */
    @Bean
    DeclarationDesAbsences absencesPatientsDeclarationDesAbsences(DeclarerUneAbsence declarer,
                                                                 RetirerLAbsenceDeLExclusion retirer) {
        return new ExclusionDeLaFacturation(declarer, retirer);
    }
}
