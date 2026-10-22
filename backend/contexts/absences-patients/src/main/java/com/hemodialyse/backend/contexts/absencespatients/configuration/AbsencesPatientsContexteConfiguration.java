package com.hemodialyse.backend.contexts.absencespatients.configuration;

import com.hemodialyse.backend.contexts.absencespatients.application.LectureDesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.ListerLesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.ListerLesSeancesDeRattrapage;
import com.hemodialyse.backend.contexts.absencespatients.application.NomsDesPatients;
import com.hemodialyse.backend.contexts.absencespatients.application.ObtenirLaSynthese;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacypatient.NomsDesPatientsJdbc;
import com.hemodialyse.backend.contexts.absencespatients.application.SeancesRealisees;
import com.hemodialyse.backend.contexts.absencespatients.application.SessionCourante;
import com.hemodialyse.backend.contexts.absencespatients.application.SuivreLaSemaine;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyseance.SeancesRealiseesJdbc;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.LectureDesAbsencesJdbc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

/**
 * Composition root du contexte Absences de patients : seul endroit qui connaît toutes les couches. Slice A1 :
 * consultation du suivi. Les noms de beans sont préfixés {@code absencesPatients} (un seul contexte Spring avec le legacy
 * et les autres contextes) ; l'horloge n'est pas un bean, pour ne pas concurrencer celle des autres contextes : le jour
 * courant est celui d'UTC, comme le legacy (le fuseau du centre arrive avec la slice A2).
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
    ListerLesAbsences absencesPatientsListerLesAbsences(LectureDesAbsences lecture, NomsDesPatients noms) {
        return new ListerLesAbsences(lecture, noms, Clock.systemUTC());
    }

    @Bean
    SuivreLaSemaine absencesPatientsSuivreLaSemaine(LectureDesAbsences lecture, SeancesRealisees seances) {
        return new SuivreLaSemaine(lecture, seances, Clock.systemUTC());
    }

    @Bean
    ObtenirLaSynthese absencesPatientsObtenirLaSynthese(LectureDesAbsences lecture) {
        return new ObtenirLaSynthese(lecture, Clock.systemUTC());
    }

    @Bean
    ListerLesSeancesDeRattrapage absencesPatientsListerLesSeancesDeRattrapage(LectureDesAbsences lecture,
                                                                             SeancesRealisees seances) {
        return new ListerLesSeancesDeRattrapage(lecture, seances, Clock.systemUTC());
    }
}
