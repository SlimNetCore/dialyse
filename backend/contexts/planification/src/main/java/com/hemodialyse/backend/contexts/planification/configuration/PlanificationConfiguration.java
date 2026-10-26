package com.hemodialyse.backend.contexts.planification.configuration;

import com.hemodialyse.backend.contexts.planification.application.ConsulterLesParametres;
import com.hemodialyse.backend.contexts.planification.application.ConsulterLesReglages;
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
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyorganisation.CreneauxDuCentreJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyorganisation.SallesDuCentreJdbc;
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

import java.time.Duration;

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
    SallesDuCentre planificationSallesDuCentre(JdbcTemplate jdbc) {
        return new SallesDuCentreJdbc(jdbc);
    }

    @Bean
    CreneauxDuCentre planificationCreneauxDuCentre(JdbcTemplate jdbc) {
        return new CreneauxDuCentreJdbc(jdbc);
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
        return new ParametresDuPlanningJdbc(jdbc, salles);
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

    /** Le langage publié de lecture du paramétrage, utilisé par la présence, la capacité de la Direction et l'optimisation. */
    @Bean
    ParametrageDeLaPlanification planificationParametrageDeLaPlanification(ParametresDuPlanningRepository parametres,
                                                                           ReglagesRepository reglages,
                                                                           PreferencesRepository preferences, ProfilsRepository profils) {
        return new ParametrageLuParLeLegacy(parametres, reglages, preferences, profils);
    }
}
