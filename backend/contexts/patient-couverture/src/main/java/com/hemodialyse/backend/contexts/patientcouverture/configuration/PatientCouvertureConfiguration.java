package com.hemodialyse.backend.contexts.patientcouverture.configuration;

import com.hemodialyse.backend.contexts.patientcouverture.application.AdmettreUnPatient;
import com.hemodialyse.backend.contexts.patientcouverture.application.AssuresDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.application.ConsulterLEffectif;
import com.hemodialyse.backend.contexts.patientcouverture.application.ConsulterLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.application.ConsulterLesMouvements;
import com.hemodialyse.backend.contexts.patientcouverture.application.CouvertureDeLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.application.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.application.GererLesAssures;
import com.hemodialyse.backend.contexts.patientcouverture.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.patientcouverture.application.Identifiants;
import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesMouvements;
import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.application.ModifierLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.application.PlaceDeLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.application.RechercherLesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.application.SessionCourante;
import com.hemodialyse.backend.contexts.patientcouverture.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementsDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.legacycentres.HorlogeDesCentresLegacy;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.legacycouverture.CouvertureDeLaFicheJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.legacygmao.GenerateursDuCentreJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.planification.PlaceDeLaFicheParLaPlanification;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.identifiants.IdentifiantsAleatoires;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.AssuresJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.LectureDesMouvementsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.LectureDesPatientsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.MouvementsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.PatientsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.ResumesEnCache;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.LiberationDesPlaces;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacementDesPatients;
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
 * Composition du contexte Patient et couverture (slice PC1, refonte sur place, ADR 0024) : mêmes tables ({@code patients},
 * {@code mouvement_patient}), mêmes URLs. Seul endroit qui connaît toutes les couches. Le port {@code NotificationsDeLaFiche} est
 * fourni par la plateforme (service de notification actuel).
 */
@Configuration
public class PatientCouvertureConfiguration {

    @Bean
    SessionCourante sessionDeLaFiche() {
        return new SessionLegacy();
    }

    @Bean
    UniteDeTravail uniteDeTravailDeLaFiche(PlatformTransactionManager gestionnaire, JdbcTemplate jdbc) {
        return new UniteDeTravailSpring(new TransactionTemplate(gestionnaire), jdbc, Duration.ofSeconds(5));
    }

    @Bean
    HorlogeDesCentres horlogeDeLaFiche(JdbcTemplate jdbc,
                                       @Value("${app.planification.fuseau-par-defaut:Africa/Algiers}") String fuseauParDefaut) {
        return new HorlogeDesCentresLegacy(jdbc, Clock.systemUTC(), ZoneId.of(fuseauParDefaut));
    }

    @Bean
    Identifiants identifiantsDeLaFiche() {
        return new IdentifiantsAleatoires();
    }

    @Bean
    PatientRepository patientsDeLaFiche(JdbcTemplate jdbc) {
        return new PatientsJdbc(jdbc);
    }

    @Bean
    MouvementsDesPatients mouvementsDeLaFiche(JdbcTemplate jdbc) {
        return new MouvementsJdbc(jdbc);
    }

    @Bean
    LectureDesPatients lectureDesPatients(JdbcTemplate jdbc) {
        return new LectureDesPatientsJdbc(jdbc);
    }

    @Bean
    LectureDesMouvements lectureDesMouvements(JdbcTemplate jdbc) {
        return new LectureDesMouvementsJdbc(jdbc);
    }

    @Bean
    AssuresDuCentre assuresDuCentre(JdbcTemplate jdbc) {
        return new AssuresJdbc(jdbc);
    }

    @Bean
    CouvertureDeLaFiche couvertureDeLaFiche(JdbcTemplate jdbc) {
        return new CouvertureDeLaFicheJdbc(jdbc);
    }

    @Bean
    GenerateursDuCentre generateursDeLaFiche(JdbcTemplate jdbc) {
        return new GenerateursDuCentreJdbc(jdbc);
    }

    @Bean
    PlaceDeLaFiche placeDeLaFiche(PlacementDesPatients placements, LiberationDesPlaces liberation) {
        return new PlaceDeLaFicheParLaPlanification(placements, liberation);
    }

    @Bean
    AdmettreUnPatient admettreUnPatient(SessionCourante session, HorlogeDesCentres horloge, Identifiants identifiants,
                                        PlaceDeLaFiche place, PatientRepository patients, MouvementsDesPatients mouvements,
                                        CouvertureDeLaFiche couverture, UniteDeTravail unite) {
        return new AdmettreUnPatient(session, horloge, identifiants, place, patients, mouvements, couverture, unite);
    }

    @Bean
    ModifierLaFiche modifierLaFiche(SessionCourante session, HorlogeDesCentres horloge, Identifiants identifiants,
                                    PlaceDeLaFiche place, PatientRepository patients, MouvementsDesPatients mouvements,
                                    CouvertureDeLaFiche couverture, UniteDeTravail unite) {
        return new ModifierLaFiche(session, horloge, identifiants, place, patients, mouvements, couverture, unite);
    }

    @Bean
    ConsulterLaFiche consulterLaFiche(SessionCourante session, PatientRepository patients, CouvertureDeLaFiche couverture,
                                      GenerateursDuCentre generateurs) {
        return new ConsulterLaFiche(session, patients, couverture, generateurs);
    }

    @Bean
    RechercherLesPatients rechercherLesPatients(SessionCourante session, LectureDesPatients lecture) {
        return new RechercherLesPatients(session, lecture);
    }

    @Bean
    ConsulterLEffectif consulterLEffectif(SessionCourante session, LectureDesPatients lecture, HorlogeDesCentres horloge) {
        return new ConsulterLEffectif(session, lecture, horloge);
    }

    @Bean
    ResumesEnCache resumesEnCache(ConsulterLEffectif effectif) {
        return new ResumesEnCache(effectif);
    }

    @Bean
    ConsulterLesMouvements consulterLesMouvements(SessionCourante session, LectureDesMouvements lecture) {
        return new ConsulterLesMouvements(session, lecture);
    }

    @Bean
    GererLesAssures gererLesAssures(SessionCourante session, HorlogeDesCentres horloge, PatientRepository patients,
                                    AssuresDuCentre assures, UniteDeTravail unite) {
        return new GererLesAssures(session, horloge, patients, assures, unite);
    }
}
