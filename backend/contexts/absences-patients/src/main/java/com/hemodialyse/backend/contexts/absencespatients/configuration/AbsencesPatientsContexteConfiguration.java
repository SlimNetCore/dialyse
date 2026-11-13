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
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.patientcouverture.ForfaitsDesPatientsDeLaCouverture;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.patientcouverture.NomsDesPatientsDeLaFiche;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.patientcouverture.PatientsDuCentreDeLaFiche;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.seance.ExclusionsDeSeancesPubliees;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.seance.SeancesRealiseesPubliees;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.LiensDExclusionFournis;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.SeancesRealiseesFournies;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.identifiants.GenerateurDIdentifiantsAleatoires;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
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
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.application.CloturerLeMoisDAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.ConsulterLesMoisDAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.GardeDesMoisDAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.PublicationDesMoisDAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.RouvrirLeMoisDAbsences;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.messaging.PublicationDesMoisDAbsencesSpring;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.MoisAbsencesJdbc;
import com.hemodialyse.backend.contexts.absencespatients.interfaces.facturation.ClotureDesAbsencesPourLaFacturation;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ClotureDesAbsences;
import org.springframework.context.ApplicationEventPublisher;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.organisation.CentresActifsPublies;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyplanning.PatientsAttendusJdbc;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.horloge.FuseauParDefaut;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.AbsencesDuPilotageJdbc;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage;
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
    SeancesRealisees absencesPatientsSeancesRealisees(SeancesRealiseesFournies seances) {
        return new SeancesRealiseesPubliees(seances);
    }

    @Bean
    NomsDesPatients absencesPatientsNomsDesPatients(FichesDesPatients fiches) {
        return new NomsDesPatientsDeLaFiche(fiches);
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
    PatientsDuCentre absencesPatientsPatientsDuCentre(FichesDesPatients fiches) {
        return new PatientsDuCentreDeLaFiche(fiches);
    }

    @Bean
    ForfaitsDesPatients absencesPatientsForfaitsDesPatients(CouvertureDesPatients couverture) {
        return new ForfaitsDesPatientsDeLaCouverture(couverture);
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
    ExclusionsDeSeances absencesPatientsExclusionsDeSeances(LiensDExclusionFournis liens) {
        return new ExclusionsDeSeancesPubliees(liens);
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
                                                          SignalementDesSaisies signalement, HorlogeDesCentres horloge,
                                                          GardeDesMoisDAbsences garde) {
        return new DeclarerUneAbsence(uniteDeTravail, patients, absences, periodes, seances, forfaits, taux, identifiants,
                signalement, horloge, garde);
    }

    @Bean
    QualifierUneAbsence absencesPatientsQualifierUneAbsence(UniteDeTravail uniteDeTravail, AbsencesRepository absences,
                                                            PeriodesFacturees periodes, SignalementDesSaisies signalement, HorlogeDesCentres horloge,
                                                            GardeDesMoisDAbsences garde) {
        return new QualifierUneAbsence(uniteDeTravail, absences, periodes, signalement, horloge, garde);
    }

    @Bean
    RattraperUneAbsence absencesPatientsRattraperUneAbsence(UniteDeTravail uniteDeTravail, AbsencesRepository absences,
                                                           PeriodesFacturees periodes, SeancesRealisees seances,
                                                            SignalementDesSaisies signalement, HorlogeDesCentres horloge,
                                                            GardeDesMoisDAbsences garde) {
        return new RattraperUneAbsence(uniteDeTravail, absences, periodes, seances, signalement, horloge, garde);
    }

    @Bean
    AnnulerUneAbsence absencesPatientsAnnulerUneAbsence(UniteDeTravail uniteDeTravail, AbsencesRepository absences,
                                                       PeriodesFacturees periodes, ExclusionsDeSeances exclusions,
                                                        SignalementDesSaisies signalement, HorlogeDesCentres horloge,
                                                        GardeDesMoisDAbsences garde) {
        return new AnnulerUneAbsence(uniteDeTravail, absences, periodes, exclusions, signalement, horloge, garde);
    }

    @Bean
    RetirerLAbsenceDeLExclusion absencesPatientsRetirerLAbsenceDeLExclusion(UniteDeTravail uniteDeTravail,
                                                                            AbsencesRepository absences, GardeDesMoisDAbsences garde) {
        return new RetirerLAbsenceDeLExclusion(uniteDeTravail, absences, garde);
    }

    // Slice A3 : détection de nuit (réconciliation, détection, notification), fuseau du centre.

    @Bean
    FuseauxDesCentres absencesPatientsFuseauxDesCentres(
            @Value("${app.absences.fuseau-par-defaut:${app.planification.fuseau-par-defaut:Africa/Algiers}}") String fuseauParDefaut) {
        return new FuseauParDefaut(ZoneId.of(fuseauParDefaut));
    }

    @Bean
    HorlogeDesCentres absencesPatientsHorlogeDesCentres(FuseauxDesCentres fuseaux) {
        return new HorlogeDesCentres(Clock.systemUTC(), fuseaux);
    }

    // Slice A6 : clôture mensuelle des absences. Un seul adaptateur pour l'état des mois (lecture de mois_absences, verrou de ligne).

    @Bean
    MoisAbsencesRepository absencesPatientsMoisAbsences(JdbcTemplate jdbc) {
        return new MoisAbsencesJdbc(jdbc);
    }

    @Bean
    GardeDesMoisDAbsences absencesPatientsGardeDesMoisDAbsences(MoisAbsencesRepository mois) {
        return new GardeDesMoisDAbsences(mois);
    }

    @Bean
    PublicationDesMoisDAbsences absencesPatientsPublicationDesMoisDAbsences(ApplicationEventPublisher publisher) {
        return new PublicationDesMoisDAbsencesSpring(publisher);
    }

    @Bean
    ConsulterLesMoisDAbsences absencesPatientsConsulterLesMoisDAbsences(MoisAbsencesRepository mois) {
        return new ConsulterLesMoisDAbsences(mois);
    }

    @Bean
    CloturerLeMoisDAbsences absencesPatientsCloturerLeMoisDAbsences(MoisAbsencesRepository mois, LectureDesAbsences lecture,
                                                                    PublicationDesMoisDAbsences publication,
                                                                    UniteDeTravail uniteDeTravail, HorlogeDesCentres horloge) {
        return new CloturerLeMoisDAbsences(mois, lecture, publication, uniteDeTravail, horloge);
    }

    @Bean
    RouvrirLeMoisDAbsences absencesPatientsRouvrirLeMoisDAbsences(MoisAbsencesRepository mois,
                                                                  PublicationDesMoisDAbsences publication,
                                                                  UniteDeTravail uniteDeTravail, HorlogeDesCentres horloge) {
        return new RouvrirLeMoisDAbsences(mois, publication, uniteDeTravail, horloge);
    }

    /**
     * Le langage publié que la Facturation attend des Absences pour avertir à la clôture d'un mois (ADR 0042).
     */
    @Bean
    ClotureDesAbsences absencesPatientsClotureDesAbsences(MoisDAbsencesOuverts mois) {
        return new ClotureDesAbsencesPourLaFacturation(mois);
    }

    @Bean
    CentresActifs absencesPatientsCentresActifs(AnnuaireDesCentres annuaire) {
        return new CentresActifsPublies(annuaire);
    }

    @Bean
    PatientsAttendus absencesPatientsPatientsAttendus(JdbcTemplate jdbc,
            com.hemodialyse.backend.contexts.planification.publishedlanguage.PresenceDesPatients presence,
            SeancesRealiseesFournies seances) {
        return new PatientsAttendusJdbc(jdbc, presence, seances);
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

    // PL13d (ADR 0156) : les messages des Absences vers notifications.v1 (alerte des absences à qualifier, saisie ABSENCE).

    @Bean
    NotificationsDuCentre absencesPatientsNotificationsDuCentre(
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps notifier,
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel diffusion,
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications journal) {
        return new com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.notifications.NotificationsDuCentreParLeCentreDeNotifications(
                notifier, diffusion, journal);
    }

    @Bean
    SignalementDesSaisies absencesPatientsSignalementDesSaisies(
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps notifier,
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel diffusion,
            FichesDesPatients fiches) {
        return new com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.notifications.SignalementDesSaisiesParLeCentreDeNotifications(
                notifier, diffusion, fiches);
    }

    // Slice A4 : lectures publiées pour le pilotage (tableau de bord des séances, vue Direction).

    @Bean
    AbsencesDuPilotage absencesPatientsAbsencesDuPilotage(JdbcTemplate jdbc,
            @Value("${app.absences.fuseau-par-defaut:${app.planification.fuseau-par-defaut:Africa/Algiers}}") String fuseauParDefaut) {
        return new AbsencesDuPilotageJdbc(jdbc, Clock.systemUTC(), ZoneId.of(fuseauParDefaut));
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
