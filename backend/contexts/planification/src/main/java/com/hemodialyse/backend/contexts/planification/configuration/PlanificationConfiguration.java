package com.hemodialyse.backend.contexts.planification.configuration;

import com.hemodialyse.backend.contexts.planification.application.ConsulterLesParametres;
import com.hemodialyse.backend.contexts.planification.application.ConsulterLesReglages;
import com.hemodialyse.backend.contexts.planification.application.ComptesUtilisateurs;
import com.hemodialyse.backend.contexts.planification.application.CreerUnCompte;
import com.hemodialyse.backend.contexts.planification.application.CreneauxDuCentre;
import com.hemodialyse.backend.contexts.planification.application.DelierAvantLaSuppressionDuCompte;
import com.hemodialyse.backend.contexts.planification.application.DelierLeCompte;
import com.hemodialyse.backend.contexts.planification.application.DesactiverUnInfirmier;
import com.hemodialyse.backend.contexts.planification.application.EnregistrerUnInfirmier;
import com.hemodialyse.backend.contexts.planification.application.FichesDetaillees;
import com.hemodialyse.backend.contexts.planification.application.GenerateurDeMotDePasse;
import com.hemodialyse.backend.contexts.planification.application.LierUnCompte;
import com.hemodialyse.backend.contexts.planification.application.ListerLesComptesLiables;
import com.hemodialyse.backend.contexts.planification.application.ListerLesInfirmiers;
import com.hemodialyse.backend.contexts.planification.application.ModifierUnInfirmier;
import com.hemodialyse.backend.contexts.planification.application.NouveauxIdentifiantsDInfirmier;
import com.hemodialyse.backend.contexts.planification.application.ReactiverUnInfirmier;
import com.hemodialyse.backend.contexts.planification.application.RoulementsDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.application.SuivreLEtatDuCompte;
import com.hemodialyse.backend.contexts.planification.application.VerrouillerLesFichesDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.application.VerrouillerLesIdentifiantsDeConnexion;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuReferentielDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyidentite.ComptesUtilisateursJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.motdepasse.GenerateurDeMotDePasseTemporaire;
import com.hemodialyse.backend.contexts.planification.infrastructure.motdepasse.NouveauxIdentifiantsAleatoires;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.InfirmiersJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation.LectureDesRessourcesPubliees;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation.VerrouDuReferentielDesInfirmiersParOrganisation;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation.VueDesSallesPubliees;
import com.hemodialyse.backend.contexts.planification.interfaces.publie.FichesDesInfirmiersPourLeLegacy;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.FichesDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.application.AffecterAuRoulement;
import com.hemodialyse.backend.contexts.planification.application.AffecterUnRemplacant;
import com.hemodialyse.backend.contexts.planification.application.AnnulerLeRemplacement;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CalendrierDuCentre;
import com.hemodialyse.backend.contexts.planification.application.ConsulterMonPlanning;
import com.hemodialyse.backend.contexts.planification.application.DeclarerMonAbsence;
import com.hemodialyse.backend.contexts.planification.application.DeclarerUneAbsenceDInfirmier;
import com.hemodialyse.backend.contexts.planification.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.planification.application.ListerLesAbsences;
import com.hemodialyse.backend.contexts.planification.application.ListerMesAbsences;
import com.hemodialyse.backend.contexts.planification.application.ModifierLAffectation;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDesAbsencesDInfirmiers;
import com.hemodialyse.backend.contexts.planification.application.NouveauxIdentifiantsDuPlanning;
import com.hemodialyse.backend.contexts.planification.application.RetirerLAffectation;
import com.hemodialyse.backend.contexts.planification.application.RetirerMonAbsence;
import com.hemodialyse.backend.contexts.planification.application.RetirerUneAbsence;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SurEffectifDuRoulement;
import com.hemodialyse.backend.contexts.planification.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.RemplacementsRepository;
import com.hemodialyse.backend.contexts.planification.domain.RoulementsRepository;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation.HorlogeDesCentresPublies;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.planification.infrastructure.motdepasse.NouveauxIdentifiantsDuPlanningAleatoires;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.AbsencesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.RemplacementsJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.RoulementEtAbsencesLusParLeLegacyJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.RoulementsJdbc;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.RoulementEtAbsencesDesInfirmiers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.hemodialyse.backend.contexts.planification.application.CreneauxDuCentre;
import com.hemodialyse.backend.contexts.planification.application.DefinirLaPreferenceDuPatient;
import com.hemodialyse.backend.contexts.planification.application.DefinirLeProfilDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.application.InfirmiersDuCentre;
import com.hemodialyse.backend.contexts.planification.application.LectureDesPreferences;
import com.hemodialyse.backend.contexts.planification.application.ListerLesPreferencesDesPatients;
import com.hemodialyse.backend.contexts.planification.application.ListerLesProfilsDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.application.ModifierLesParametres;
import com.hemodialyse.backend.contexts.planification.application.ModifierLesReglages;
import com.hemodialyse.backend.contexts.planification.application.PatientsDuCentre;
import com.hemodialyse.backend.contexts.planification.application.SallesDuCentre;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanningRepository;
import com.hemodialyse.backend.contexts.planification.domain.PreferencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.ProfilsRepository;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesRepository;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuCentre;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyinfirmier.InfirmiersDuCentreJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation.CreneauxDuCentrePublies;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation.SallesDuCentrePubliees;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient.PatientsDuCentreJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.LectureDesPreferencesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.ParametresDuPlanningJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.PreferencesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.ProfilsJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.ReglagesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.VerrouDuCentreJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.planification.interfaces.publie.ParametrageLuParLeLegacy;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ParametrageDeLaPlanification;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

/**
 * Composition root du contexte Planification : seul endroit qui connaît toutes les couches. Slice P1 : paramétrage (paramètres du
 * planning, réglages, préférences des patients, profils des infirmiers) et sa lecture publiée. Les noms de beans sont préfixés
 * {@code planification} (un seul contexte Spring avec le legacy et les autres contextes). Toujours active : aucune façade, aucun
 * routage (ADR 0024).
 */
@Configuration(proxyBeanMethods = false)
public class PlanificationConfiguration {

    @Bean
    SessionCourante planificationSessionCourante() {
        return new SessionLegacy();
    }

    @Bean
    UniteDeTravail planificationUniteDeTravail(PlatformTransactionManager transactionManager, JdbcTemplate jdbc) {
        return new UniteDeTravailSpring(new TransactionTemplate(transactionManager), jdbc, Duration.ofSeconds(5));
    }

    @Bean
    VerrouDuCentre planificationVerrouDuCentre(JdbcTemplate jdbc) {
        return new VerrouDuCentreJdbc(jdbc);
    }

    @Bean
    SallesDuCentre planificationSallesDuCentre(
            com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre organisation,
            com.hemodialyse.backend.contexts.organisation.publishedlanguage.DefinirLesSallesDIsolement isolement) {
        return new SallesDuCentrePubliees(organisation, isolement);
    }

    @Bean
    CreneauxDuCentre planificationCreneauxDuCentre(
            com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre organisation) {
        return new CreneauxDuCentrePublies(organisation);
    }

    @Bean
    PatientsDuCentre planificationPatientsDuCentre(JdbcTemplate jdbc) {
        return new PatientsDuCentreJdbc(jdbc);
    }

    @Bean
    InfirmiersDuCentre planificationInfirmiersDuCentre(JdbcTemplate jdbc) {
        return new InfirmiersDuCentreJdbc(jdbc);
    }

    @Bean
    ParametresDuPlanningRepository planificationParametres(JdbcTemplate jdbc, SallesDuCentre salles) {
        return new ParametresDuPlanningJdbc(jdbc, salles, Clock.systemUTC());
    }

    @Bean
    ReglagesRepository planificationReglages(JdbcTemplate jdbc) {
        return new ReglagesJdbc(jdbc);
    }

    @Bean
    PreferencesRepository planificationPreferences(JdbcTemplate jdbc) {
        return new PreferencesJdbc(jdbc);
    }

    @Bean
    ProfilsRepository planificationProfils(JdbcTemplate jdbc) {
        return new ProfilsJdbc(jdbc);
    }

    @Bean
    LectureDesPreferences planificationLectureDesPreferences(JdbcTemplate jdbc) {
        return new LectureDesPreferencesJdbc(jdbc);
    }

    @Bean
    ConsulterLesParametres planificationConsulterLesParametres(ParametresDuPlanningRepository parametres) {
        return new ConsulterLesParametres(parametres);
    }

    @Bean
    ModifierLesParametres planificationModifierLesParametres(UniteDeTravail unite, VerrouDuCentre verrou,
                                                             ParametresDuPlanningRepository parametres, SallesDuCentre salles) {
        return new ModifierLesParametres(unite, verrou, parametres, salles);
    }

    @Bean
    ConsulterLesReglages planificationConsulterLesReglages(ReglagesRepository reglages) {
        return new ConsulterLesReglages(reglages);
    }

    @Bean
    ModifierLesReglages planificationModifierLesReglages(UniteDeTravail unite, VerrouDuCentre verrou, ReglagesRepository reglages) {
        return new ModifierLesReglages(unite, verrou, reglages);
    }

    @Bean
    ListerLesPreferencesDesPatients planificationListerLesPreferencesDesPatients(LectureDesPreferences lecture) {
        return new ListerLesPreferencesDesPatients(lecture);
    }

    @Bean
    DefinirLaPreferenceDuPatient planificationDefinirLaPreferenceDuPatient(UniteDeTravail unite, VerrouDuCentre verrou,
                                                                           PreferencesRepository preferences,
                                                                           CreneauxDuCentre creneaux, PatientsDuCentre patients) {
        return new DefinirLaPreferenceDuPatient(unite, verrou, preferences, creneaux, patients);
    }

    @Bean
    ListerLesProfilsDesInfirmiers planificationListerLesProfilsDesInfirmiers(LectureDesPreferences lecture) {
        return new ListerLesProfilsDesInfirmiers(lecture);
    }

    @Bean
    DefinirLeProfilDeLInfirmier planificationDefinirLeProfilDeLInfirmier(UniteDeTravail unite, VerrouDuCentre verrou,
                                                                         ProfilsRepository profils, InfirmiersDuCentre infirmiers) {
        return new DefinirLeProfilDeLInfirmier(unite, verrou, profils, infirmiers);
    }

    // ------------------------------------------------------------------ slice P2 : référentiel des infirmiers et comptes

    @Bean
    VerrouDuReferentielDesInfirmiers planificationVerrouDuReferentielDesInfirmiers(
            com.hemodialyse.backend.contexts.organisation.publishedlanguage.VerrouDuCentre verrouDuCentre) {
        return new VerrouDuReferentielDesInfirmiersParOrganisation(verrouDuCentre);
    }

    @Bean
    InfirmiersRepository planificationInfirmiers(JdbcTemplate jdbc) {
        return new InfirmiersJdbc(jdbc);
    }

    @Bean
    ComptesUtilisateurs planificationComptesUtilisateurs(JdbcTemplate jdbc, PasswordEncoder encodeur) {
        return new ComptesUtilisateursJdbc(jdbc, encodeur);
    }

    @Bean
    RoulementsJdbc planificationRoulements(JdbcTemplate jdbc) {
        return new RoulementsJdbc(jdbc);
    }

    @Bean
    GenerateurDeMotDePasse planificationGenerateurDeMotDePasse() {
        return new GenerateurDeMotDePasseTemporaire();
    }

    @Bean
    NouveauxIdentifiantsDInfirmier planificationNouveauxIdentifiantsDInfirmier() {
        return new NouveauxIdentifiantsAleatoires();
    }

    @Bean
    FichesDetaillees planificationFichesDetaillees(RoulementsDesInfirmiers roulements, ComptesUtilisateurs comptes) {
        return new FichesDetaillees(roulements, comptes);
    }

    @Bean
    ListerLesInfirmiers planificationListerLesInfirmiers(InfirmiersRepository infirmiers, FichesDetaillees fiches) {
        return new ListerLesInfirmiers(infirmiers, fiches);
    }

    @Bean
    EnregistrerUnInfirmier planificationEnregistrerUnInfirmier(UniteDeTravail unite, VerrouDuReferentielDesInfirmiers verrou,
                                                               InfirmiersRepository infirmiers,
                                                               NouveauxIdentifiantsDInfirmier identifiants, FichesDetaillees fiches) {
        return new EnregistrerUnInfirmier(unite, verrou, infirmiers, identifiants, fiches);
    }

    @Bean
    ModifierUnInfirmier planificationModifierUnInfirmier(UniteDeTravail unite, VerrouDuReferentielDesInfirmiers verrou,
                                                         InfirmiersRepository infirmiers, FichesDetaillees fiches) {
        return new ModifierUnInfirmier(unite, verrou, infirmiers, fiches);
    }

    @Bean
    DesactiverUnInfirmier planificationDesactiverUnInfirmier(UniteDeTravail unite, VerrouDuReferentielDesInfirmiers verrou,
                                                             InfirmiersRepository infirmiers, ComptesUtilisateurs comptes,
                                                             FichesDetaillees fiches) {
        return new DesactiverUnInfirmier(unite, verrou, infirmiers, comptes, fiches);
    }

    @Bean
    ReactiverUnInfirmier planificationReactiverUnInfirmier(UniteDeTravail unite, VerrouDuReferentielDesInfirmiers verrou,
                                                           InfirmiersRepository infirmiers, ComptesUtilisateurs comptes,
                                                           FichesDetaillees fiches) {
        return new ReactiverUnInfirmier(unite, verrou, infirmiers, comptes, fiches);
    }

    @Bean
    ListerLesComptesLiables planificationListerLesComptesLiables(ComptesUtilisateurs comptes) {
        return new ListerLesComptesLiables(comptes);
    }

    @Bean
    LierUnCompte planificationLierUnCompte(UniteDeTravail unite, VerrouDuReferentielDesInfirmiers verrou,
                                           InfirmiersRepository infirmiers, ComptesUtilisateurs comptes, FichesDetaillees fiches) {
        return new LierUnCompte(unite, verrou, infirmiers, comptes, fiches);
    }

    @Bean
    CreerUnCompte planificationCreerUnCompte(UniteDeTravail unite, VerrouDuReferentielDesInfirmiers verrou,
                                             InfirmiersRepository infirmiers, ComptesUtilisateurs comptes,
                                             GenerateurDeMotDePasse motsDePasse, FichesDetaillees fiches) {
        return new CreerUnCompte(unite, verrou, infirmiers, comptes, motsDePasse, fiches);
    }

    @Bean
    DelierLeCompte planificationDelierLeCompte(UniteDeTravail unite, VerrouDuReferentielDesInfirmiers verrou,
                                               InfirmiersRepository infirmiers, ComptesUtilisateurs comptes,
                                               FichesDetaillees fiches) {
        return new DelierLeCompte(unite, verrou, infirmiers, comptes, fiches);
    }

    @Bean
    SuivreLEtatDuCompte planificationSuivreLEtatDuCompte(UniteDeTravail unite, InfirmiersRepository infirmiers) {
        return new SuivreLEtatDuCompte(unite, infirmiers);
    }

    @Bean
    DelierAvantLaSuppressionDuCompte planificationDelierAvantLaSuppressionDuCompte(UniteDeTravail unite,
                                                                                   InfirmiersRepository infirmiers) {
        return new DelierAvantLaSuppressionDuCompte(unite, infirmiers);
    }

    /** Le langage publié d'écriture vers l'administration des utilisateurs (synchronisation compte → fiche, suppression). */
    @Bean
    VerrouillerLesIdentifiantsDeConnexion planificationVerrouillerLesIdentifiantsDeConnexion(UniteDeTravail unite,
                                                                                            ComptesUtilisateurs comptes) {
        return new VerrouillerLesIdentifiantsDeConnexion(unite, comptes);
    }

    @Bean
    VerrouillerLesFichesDesInfirmiers planificationVerrouillerLesFichesDesInfirmiers(UniteDeTravail unite, InfirmiersRepository infirmiers) {
        return new VerrouillerLesFichesDesInfirmiers(unite, infirmiers);
    }

    @Bean
    FichesDesInfirmiers planificationFichesDesInfirmiers(SuivreLEtatDuCompte suivre, DelierAvantLaSuppressionDuCompte delier,
                                                         VerrouillerLesIdentifiantsDeConnexion identifiants,
                                                         VerrouillerLesFichesDesInfirmiers fiches) {
        return new FichesDesInfirmiersPourLeLegacy(suivre, delier, identifiants, fiches);
    }

    // ------------------------------------------------------------------ slice P3 : roulement, absences, remplacements, Mon planning

    @Bean
    AbsencesRepository planificationAbsences(JdbcTemplate jdbc) {
        return new AbsencesJdbc(jdbc);
    }

    @Bean
    RemplacementsRepository planificationRemplacements(JdbcTemplate jdbc) {
        return new RemplacementsJdbc(jdbc);
    }

    @Bean
    NouveauxIdentifiantsDuPlanning planificationNouveauxIdentifiantsDuPlanning() {
        return new NouveauxIdentifiantsDuPlanningAleatoires();
    }

    /**
     * « Aujourd'hui » du centre : son fuseau ({@code centers.fuseau_horaire}), à défaut {@code Africa/Algiers} (ADR 0054).
     */
    @Bean
    HorlogeDesCentres planificationHorlogeDesCentres(AnnuaireDesCentres annuaire,
                                                     @Value("${app.planification.fuseau-par-defaut:Africa/Algiers}") String fuseauParDefaut) {
        return new HorlogeDesCentresPublies(annuaire, Clock.systemUTC(), ZoneId.of(fuseauParDefaut));
    }

    @Bean
    AffecterAuRoulement planificationAffecterAuRoulement(UniteDeTravail unite, InfirmiersRepository infirmiers,
                                                         RoulementsRepository roulements, SallesDuCentre salles,
                                                         CreneauxDuCentre creneaux, NouveauxIdentifiantsDuPlanning identifiants,
                                                         SurEffectifDuRoulement surEffectif, HorlogeDesCentres horloge) {
        return new AffecterAuRoulement(unite, infirmiers, roulements, salles, creneaux, identifiants, surEffectif, horloge);
    }

    @Bean
    ModifierLAffectation planificationModifierLAffectation(UniteDeTravail unite, InfirmiersRepository infirmiers,
                                                           RoulementsRepository roulements, SallesDuCentre salles,
                                                           CreneauxDuCentre creneaux, SurEffectifDuRoulement surEffectif,
                                                           HorlogeDesCentres horloge) {
        return new ModifierLAffectation(unite, infirmiers, roulements, salles, creneaux, surEffectif, horloge);
    }

    @Bean
    RetirerLAffectation planificationRetirerLAffectation(UniteDeTravail unite, InfirmiersRepository infirmiers,
                                                         RoulementsRepository roulements) {
        return new RetirerLAffectation(unite, infirmiers, roulements);
    }

    @Bean
    ListerLesAbsences planificationListerLesAbsences(AbsencesRepository absences) {
        return new ListerLesAbsences(absences);
    }

    @Bean
    DeclarerUneAbsenceDInfirmier planificationDeclarerUneAbsenceDInfirmier(UniteDeTravail unite, InfirmiersRepository infirmiers,
                                                                           AbsencesRepository absences,
                                                                           NouveauxIdentifiantsDuPlanning identifiants,
                                                                           NotificationsDesAbsencesDInfirmiers notifications) {
        return new DeclarerUneAbsenceDInfirmier(unite, infirmiers, absences, identifiants, notifications);
    }

    @Bean
    RetirerUneAbsence planificationRetirerUneAbsence(UniteDeTravail unite, AbsencesRepository absences,
                                                     InfirmiersRepository infirmiers, NotificationsDesAbsencesDInfirmiers notifications) {
        return new RetirerUneAbsence(unite, absences, infirmiers, notifications);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.PlanningDesPlaces planificationPlanningDesPlaces(
            JdbcTemplate jdbc, com.hemodialyse.backend.contexts.planification.application.LectureDesRessources ressources) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient.PlanningDesPlacesDuCentreJdbc(
                jdbc, ressources);
    }

    @Bean
    CalendrierDuCentre planificationCalendrierDuCentre(
            com.hemodialyse.backend.contexts.planification.application.LectureDesRessources ressources) {
        return new com.hemodialyse.backend.contexts.planification.application.CalendrierDuCentreDuModule(ressources);
    }

    @Bean
    SemaineDeLInfirmier planificationSemaineDeLInfirmier(
            com.hemodialyse.backend.contexts.planification.application.ConsulterLaPresence presence,
            com.hemodialyse.backend.contexts.planification.application.ConsulterLaSemaine planning) {
        return new com.hemodialyse.backend.contexts.planification.application.SemaineDeLInfirmierDuModule(presence, planning);
    }

    @Bean
    ConsulterMonPlanning planificationConsulterMonPlanning(InfirmiersRepository infirmiers, FichesDetaillees fiches,
                                                           SemaineDeLInfirmier semaines, HorlogeDesCentres horloge) {
        return new ConsulterMonPlanning(infirmiers, fiches, semaines, horloge);
    }

    @Bean
    ListerMesAbsences planificationListerMesAbsences(InfirmiersRepository infirmiers, AbsencesRepository absences) {
        return new ListerMesAbsences(infirmiers, absences);
    }

    @Bean
    DeclarerMonAbsence planificationDeclarerMonAbsence(UniteDeTravail unite, InfirmiersRepository infirmiers,
                                                       AbsencesRepository absences, NouveauxIdentifiantsDuPlanning identifiants,
                                                       NotificationsDesAbsencesDInfirmiers notifications, HorlogeDesCentres horloge) {
        return new DeclarerMonAbsence(unite, infirmiers, absences, identifiants, notifications, horloge);
    }

    @Bean
    RetirerMonAbsence planificationRetirerMonAbsence(UniteDeTravail unite, InfirmiersRepository infirmiers,
                                                     AbsencesRepository absences, NotificationsDesAbsencesDInfirmiers notifications,
                                                     HorlogeDesCentres horloge) {
        return new RetirerMonAbsence(unite, infirmiers, absences, notifications, horloge);
    }

    @Bean
    AffecterUnRemplacant planificationAffecterUnRemplacant(UniteDeTravail unite, InfirmiersRepository infirmiers,
                                                           RoulementsRepository roulements, AbsencesRepository absences,
                                                           RemplacementsRepository remplacements, SallesDuCentre salles,
                                                           CreneauxDuCentre creneaux, CalendrierDuCentre calendrier,
                                                           HorlogeDesCentres horloge, NouveauxIdentifiantsDuPlanning identifiants) {
        return new AffecterUnRemplacant(unite, infirmiers, roulements, absences, remplacements, salles, creneaux, calendrier, horloge,
                identifiants);
    }

    @Bean
    AnnulerLeRemplacement planificationAnnulerLeRemplacement(UniteDeTravail unite, RemplacementsRepository remplacements) {
        return new AnnulerLeRemplacement(unite, remplacements);
    }

    /**
     * Le langage publié de lecture du roulement, des absences et des remplacements (présence, charge, optimisation du legacy).
     */
    @Bean
    RoulementEtAbsencesDesInfirmiers planificationRoulementEtAbsencesDesInfirmiers(JdbcTemplate jdbc) {
        return new RoulementEtAbsencesLusParLeLegacyJdbc(jdbc);
    }

    /** Le langage publié de lecture du paramétrage, utilisé par la présence, la capacité de la Direction et l'optimisation. */
    @Bean
    ParametrageDeLaPlanification planificationParametrageDeLaPlanification(ParametresDuPlanningRepository parametres,
                                                                           ReglagesRepository reglages,
                                                                           PreferencesRepository preferences, ProfilsRepository profils) {
        return new ParametrageLuParLeLegacy(parametres, reglages, preferences, profils);
    }

    // ------------------------------------------------------------------ slice P4 : présence, alertes, charge, impression, contrôle de 07:15

    @Bean
    com.hemodialyse.backend.contexts.planification.application.PersonnelDeLaPresence planificationPersonnelDeLaPresence(
            JdbcTemplate jdbc, RoulementEtAbsencesDesInfirmiers roulement) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.persistence.PersonnelDeLaPresenceJdbc(jdbc, roulement);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.CentresActifs planificationCentresActifs(AnnuaireDesCentres annuaire) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation.CentresActifsPublies(annuaire);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.ConsulterLaPresence planificationConsulterLaPresence(
            com.hemodialyse.backend.contexts.planification.application.PersonnelDeLaPresence personnel,
            com.hemodialyse.backend.contexts.planification.publishedlanguage.PlanningDesPlaces places, HorlogeDesCentres horloge) {
        return new com.hemodialyse.backend.contexts.planification.application.ConsulterLaPresence(personnel, places, horloge);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.ClasserLesRemplacants planificationClasserLesRemplacants(
            com.hemodialyse.backend.contexts.planification.application.PersonnelDeLaPresence personnel,
            com.hemodialyse.backend.contexts.planification.publishedlanguage.PlanningDesPlaces places, SallesDuCentre salles,
            CreneauxDuCentre creneaux, CalendrierDuCentre calendrier, HorlogeDesCentres horloge) {
        return new com.hemodialyse.backend.contexts.planification.application.ClasserLesRemplacants(personnel, places, salles, creneaux,
                calendrier, horloge);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.ConsulterLaChargeDesInfirmiers planificationConsulterLaCharge(
            com.hemodialyse.backend.contexts.planification.application.PersonnelDeLaPresence personnel,
            com.hemodialyse.backend.contexts.planification.publishedlanguage.PlanningDesPlaces places, HorlogeDesCentres horloge) {
        return new com.hemodialyse.backend.contexts.planification.application.ConsulterLaChargeDesInfirmiers(personnel, places, horloge);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.ImprimerLaPresence planificationImprimerLaPresence(
            com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDeLaPresence impression, HorlogeDesCentres horloge) {
        return new com.hemodialyse.backend.contexts.planification.application.ImprimerLaPresence(impression, horloge);
    }

    /** Le sur-effectif du roulement (P3) est calculé par la présence du contexte. */
    @Bean
    SurEffectifDuRoulement planificationSurEffectifDuRoulement(
            com.hemodialyse.backend.contexts.planification.application.PersonnelDeLaPresence personnel,
            com.hemodialyse.backend.contexts.planification.publishedlanguage.PlanningDesPlaces places) {
        return new com.hemodialyse.backend.contexts.planification.application.SurEffectifCalculeParLaPresence(personnel, places);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.ControlerLaPresence planificationControlerLaPresence(
            com.hemodialyse.backend.contexts.planification.application.CentresActifs centres, HorlogeDesCentres horloge,
            com.hemodialyse.backend.contexts.planification.application.PersonnelDeLaPresence personnel,
            com.hemodialyse.backend.contexts.planification.publishedlanguage.PlanningDesPlaces places, ReglagesRepository reglages,
            com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDeLaPresence notifications) {
        return new com.hemodialyse.backend.contexts.planification.application.ControlerLaPresence(centres, horloge, personnel, places,
                reglages, notifications);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.interfaces.jobs.ControleDeLaPresenceDe0715 planificationControleDeLaPresenceDe0715(
            com.hemodialyse.backend.contexts.planification.application.ControlerLaPresence controler) {
        return new com.hemodialyse.backend.contexts.planification.interfaces.jobs.ControleDeLaPresenceDe0715(controler);
    }

    // ───────────── P7 : libération des places (ADR 0061) ─────────────
    @Bean
    com.hemodialyse.backend.contexts.planification.application.PlacesTenues planificationPlacesTenues(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient.PlacesTenuesJdbc(jdbc);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.MouvementsDesPatients planificationMouvementsDesPatients(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient.MouvementsDesPatientsJdbc(jdbc);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.LibererLesPlacesEchues planificationLibererLesPlacesEchues(
            com.hemodialyse.backend.contexts.planification.application.CentresActifs centres, HorlogeDesCentres horloge,
            UniteDeTravail unite, com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces verrou,
            com.hemodialyse.backend.contexts.planification.application.PlacesTenues places,
            com.hemodialyse.backend.contexts.planification.application.MouvementsDesPatients mouvements,
            com.hemodialyse.backend.contexts.planification.publishedlanguage.CachesDesPatients caches,
            com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDeLaLiberation notifications) {
        return new com.hemodialyse.backend.contexts.planification.application.LibererLesPlacesEchues(centres, horloge, unite, verrou,
                places, mouvements, caches, notifications, Clock.systemUTC());
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.LiberationDesPlaces planificationLiberationDesPlaces(
            HorlogeDesCentres horloge, UniteDeTravail unite, com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces verrou,
            com.hemodialyse.backend.contexts.planification.application.PlacesTenues places,
            com.hemodialyse.backend.contexts.planification.application.MouvementsDesPatients mouvements,
            com.hemodialyse.backend.contexts.planification.publishedlanguage.CachesDesPatients caches) {
        return new com.hemodialyse.backend.contexts.planification.application.LibererLaPlaceDeLaFiche(horloge, unite, verrou, places,
                mouvements, caches, Clock.systemUTC());
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.interfaces.jobs.LiberationDesPlacesDe0400 planificationLiberationDesPlacesDe0400(
            com.hemodialyse.backend.contexts.planification.application.LibererLesPlacesEchues liberer) {
        return new com.hemodialyse.backend.contexts.planification.interfaces.jobs.LiberationDesPlacesDe0400(liberer);
    }

    // ───────────── P5 : placement (ADR 0049, 0052, 0053) ─────────────
    @Bean
    com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces planificationVerrouDesPlaces(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.persistence.VerrouDesPlacesJdbc(jdbc, Clock.systemUTC());
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository planificationPlacesDuCentre(JdbcTemplate jdbc,
            com.hemodialyse.backend.contexts.planification.publishedlanguage.RisquesInfectieuxFournis risques) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient.PlacesDuCentreJdbc(jdbc, risques);
    }

    /** DM9 (ADR 0101) : le risque infectieux vient du langage publié du Dossier médical, fourni par la plateforme ; plus de SQL ici. */
    @Bean
    com.hemodialyse.backend.contexts.planification.application.RisquesInfectieux planificationRisquesInfectieux(
            com.hemodialyse.backend.contexts.planification.publishedlanguage.RisquesInfectieuxFournis risques) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.dossiermedical.RisquesInfectieuxPublies(risques);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.NomsDesPatients planificationNomsDesPatients(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient.NomsDesPatientsJdbc(jdbc);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.LectureDesRessources planificationLectureDesRessources(
            JdbcTemplate jdbc,
            com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre organisation,
            com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre generateurs,
            ParametresDuPlanningRepository parametres) {
        return new LectureDesRessourcesPubliees(jdbc, organisation, generateurs, parametres);
    }

    @Bean
    VueDesSallesPubliees planificationVueDesSalles(
            com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre organisation,
            com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre generateurs) {
        return new VueDesSallesPubliees(organisation, generateurs);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteDesSalles planificationCapaciteDesSalles(
            com.hemodialyse.backend.contexts.planification.application.SalleDuCentre salles) {
        return new com.hemodialyse.backend.contexts.planification.application.ControlerLaCapaciteDeLaSalle(salles);
    }

    // ACL sortantes vers notifications.v1 (PL13c, ADR 0155) : le catalogue des messages est dans la Planification.

    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDeLaPresence planificationNotificationsDeLaPresence(
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps notifier,
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel diffusion,
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications journal) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.notifications.NotificationsDeLaPresenceParLeCentreDeNotifications(
                notifier, diffusion, journal);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDeLaLiberation planificationNotificationsDeLaLiberation(
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps notifier,
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel diffusion,
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications journal) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.notifications.NotificationsDeLaLiberationParLeCentreDeNotifications(
                notifier, diffusion, journal);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.AlertesDuPlacement planificationAlertesDuPlacement(
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps notifier,
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel diffusion,
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications journal) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.notifications.AlertesDuPlacementParLeCentreDeNotifications(
                notifier, diffusion, journal);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.AlertesDeLOptimisation planificationAlertesDeLOptimisation(
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps notifier,
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel diffusion,
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications journal) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.notifications.AlertesDeLOptimisationParLeCentreDeNotifications(
                notifier, diffusion, journal);
    }

    @Bean
    NotificationsDesAbsencesDInfirmiers planificationNotificationsDesAbsencesDInfirmiers(
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps notifier,
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel diffusion,
            com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications journal) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.notifications.NotificationsDesAbsencesDInfirmiersParLeCentreDeNotifications(
                notifier, diffusion, journal);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.NotificationsDuPlacement planificationNotificationsDuPlacement(
            com.hemodialyse.backend.contexts.planification.publishedlanguage.AlertesDuPlacement alertes) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacynotifications.NotificationsDuPlacementLegacy(
                alertes);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacementDesPatients planificationPlacementDesPatients(
            UniteDeTravail unite, com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces verrou,
            com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository places,
            com.hemodialyse.backend.contexts.planification.application.LectureDesRessources ressources,
            com.hemodialyse.backend.contexts.planification.application.RisquesInfectieux risques, HorlogeDesCentres horloge) {
        return new com.hemodialyse.backend.contexts.planification.application.PlacerLePatient(unite, verrou, places, ressources,
                risques, horloge);
    }

    /** Verrou des places pour l'application d'une proposition d'optimisation (legacy, P8) : exclusion mutuelle avec la fiche. */
    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.VerrouDesPlacesDuCentre planificationVerrouDesPlacesDuCentre(
            UniteDeTravail unite, com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces verrou) {
        return new com.hemodialyse.backend.contexts.planification.application.VerrouillerLesPlaces(unite, verrou);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.ReaffectationEnIsolement planificationReaffectationEnIsolement(
            UniteDeTravail unite, com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces verrou,
            com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository places,
            com.hemodialyse.backend.contexts.planification.application.LectureDesRessources ressources,
            com.hemodialyse.backend.contexts.planification.application.RisquesInfectieux risques, PatientsDuCentre patients,
            com.hemodialyse.backend.contexts.planification.application.NomsDesPatients noms,
            com.hemodialyse.backend.contexts.planification.application.NotificationsDuPlacement notifications,
            HorlogeDesCentres horloge) {
        return new com.hemodialyse.backend.contexts.planification.application.ReaffecterEnIsolementSiRisque(unite, verrou, places,
                ressources, risques, patients, noms, notifications, horloge);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.AlerterUnGenerateurIndisponible planificationAlerterUnGenerateurIndisponible(
            UniteDeTravail unite, com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository places,
            com.hemodialyse.backend.contexts.planification.application.NomsDesPatients noms,
            com.hemodialyse.backend.contexts.planification.application.NotificationsDuPlacement notifications,
            HorlogeDesCentres horloge) {
        return new com.hemodialyse.backend.contexts.planification.application.AlerterUnGenerateurIndisponible(unite, places, noms,
                notifications, horloge);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.ProposerDesPlaces planificationProposerDesPlaces(
            UniteDeTravail unite, com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository places,
            com.hemodialyse.backend.contexts.planification.application.LectureDesRessources ressources,
            com.hemodialyse.backend.contexts.planification.application.RisquesInfectieux risques, HorlogeDesCentres horloge) {
        return new com.hemodialyse.backend.contexts.planification.application.ProposerDesPlaces(unite, places, ressources, risques,
                horloge);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacesDesPatients planificationPlacesDesPatients(
            com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository places) {
        return new com.hemodialyse.backend.contexts.planification.application.ConsulterLesPlacesDesPatients(places);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteDesCentres planificationCapaciteDesCentres(
            UniteDeTravail unite, com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository places,
            com.hemodialyse.backend.contexts.planification.application.LectureDesRessources ressources) {
        return new com.hemodialyse.backend.contexts.planification.application.ConsulterLaCapacite(unite, places, ressources);
    }

    // ---- Slice P6 : planning de la semaine et lectures publiées (présence, base de la capacité théorique) ----

    @Bean
    com.hemodialyse.backend.contexts.planification.application.SejoursDesPatients planificationSejoursDesPatients(JdbcTemplate jdbc,
            com.hemodialyse.backend.contexts.planification.publishedlanguage.RisquesInfectieuxFournis risques) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient.SejoursDesPatientsJdbc(jdbc, risques);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.SeancesRealisees planificationSeancesRealisees(
            com.hemodialyse.backend.contexts.planification.publishedlanguage.SeancesRealiseesAvecLeurPlaceFournies seances,
            com.hemodialyse.backend.contexts.planification.publishedlanguage.RisquesInfectieuxFournis risques) {
        // SE7d (ADR 0098) : les séances réalisées viennent de seance.v1 ; DM9 (ADR 0101) : le risque infectieux de dossier-medical.v1
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.seance.SeancesRealiseesPubliees(seances,
                risques::patientsARisque);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.DeplacementsTemporaires planificationDeplacementsTemporaires(
            JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyoptimisation.DeplacementsTemporairesJdbc(jdbc);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.ConsulterLaSemaine planificationConsulterLaSemaine(
            com.hemodialyse.backend.contexts.planification.application.LectureDesRessources ressources,
            com.hemodialyse.backend.contexts.planification.application.SejoursDesPatients patients,
            com.hemodialyse.backend.contexts.planification.application.SeancesRealisees seances,
            com.hemodialyse.backend.contexts.planification.application.DeplacementsTemporaires deplacements, HorlogeDesCentres horloge) {
        return new com.hemodialyse.backend.contexts.planification.application.ConsulterLaSemaine(ressources, patients, seances,
                deplacements, horloge);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.PresenceDesPatients planificationPresenceDesPatients(
            com.hemodialyse.backend.contexts.planification.application.SejoursDesPatients patients) {
        return new com.hemodialyse.backend.contexts.planification.application.ConsulterLaPresenceDesPatients(patients);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.PatientsAuxGenerateurs planificationPatientsAuxGenerateurs(
            com.hemodialyse.backend.contexts.planification.application.SejoursDesPatients patients) {
        return new com.hemodialyse.backend.contexts.planification.application.CompterLesPatientsAuxGenerateurs(patients);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.BaseDeLaCapaciteTheorique planificationBaseDeLaCapaciteTheorique(
            com.hemodialyse.backend.contexts.planification.application.SeriesDuCentre series, ParametresDuPlanningRepository parametres,
            com.hemodialyse.backend.contexts.planification.publishedlanguage.PresenceDesPatients presence) {
        return new com.hemodialyse.backend.contexts.planification.application.ConsulterLaBaseDeLaCapaciteTheorique(series, parametres,
                presence);
    }

    /** SE7f (ADR 0100) : la capacité théorique quitte bc-seance ; la Direction la lit par ce port (générateurs en service fournis par la GMAO). */
    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteTheoriqueDesCentres planificationCapaciteTheoriqueDesCentres(
            com.hemodialyse.backend.contexts.planification.publishedlanguage.BaseDeLaCapaciteTheorique base) {
        return new com.hemodialyse.backend.contexts.planification.application.ConsulterLaCapaciteTheorique(base);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.SeriesDuCentre planificationSeriesDuCentre(
            com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre organisation) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation.SeriesDuCentrePubliees(organisation);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.application.ConsulterLesSalles planificationConsulterLesSalles(
            com.hemodialyse.backend.contexts.planification.application.VueDesSalles salles) {
        return new com.hemodialyse.backend.contexts.planification.application.ConsulterLesSalles(salles);
    }

    @Bean
    com.hemodialyse.backend.contexts.planification.interfaces.evenements.GenerateurDevenuIndisponibleEcouteur planificationGenerateurDevenuIndisponibleEcouteur(
            com.hemodialyse.backend.contexts.planification.application.AlerterUnGenerateurIndisponible alerter) {
        return new com.hemodialyse.backend.contexts.planification.interfaces.evenements.GenerateurDevenuIndisponibleEcouteur(alerter);
    }

    /** PL11-2 (ADR 0158) : ce que la Planification retient d'un créneau de l'Organisation (suppression ou désactivation). */
    @Bean
    com.hemodialyse.backend.contexts.organisation.publishedlanguage.UsageDuCreneau usageDuCreneauParLaPlanification(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation.UsageDuCreneauParLaPlanification(jdbc);
    }

    /** PL11-4 (ADR 0160) : ce que la Planification retient d'une salle de l'Organisation (suppression ou désactivation). */
    @Bean
    com.hemodialyse.backend.contexts.organisation.publishedlanguage.UsageDeLaSalle usageDeLaSalleParLaPlanification(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation.UsageDeLaSalleParLaPlanification(jdbc);
    }
}
