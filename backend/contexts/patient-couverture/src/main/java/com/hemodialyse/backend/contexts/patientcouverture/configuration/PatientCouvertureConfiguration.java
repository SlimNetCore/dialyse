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
import com.hemodialyse.backend.contexts.patientcouverture.application.AttestationsDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.application.GererLesPec;
import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesPec;
import com.hemodialyse.backend.contexts.patientcouverture.application.PecDeLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.application.RechercherLesPec;
import com.hemodialyse.backend.contexts.patientcouverture.application.PrincipalDeLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PrisesEnCharge;
import com.hemodialyse.backend.contexts.patientcouverture.application.GererLesAttestations;
import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesAttestations;
import com.hemodialyse.backend.contexts.patientcouverture.application.RechercherLesAttestations;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Attestations;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.AttestationsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.LectureDesAttestationsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.ListesDAttestationsEnCache;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.LectureDesPecJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.PrisesEnChargeJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.ListesDePecEnCache;
import com.hemodialyse.backend.contexts.patientcouverture.application.RechercherLesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.application.SessionCourante;
import com.hemodialyse.backend.contexts.patientcouverture.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.patientcouverture.application.VerrouDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.planification.VerrouDuCentreParLaPlanification;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.VerrouDesPlacesDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementsDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.legacycentres.HorlogeDesCentresLegacy;
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
    CouvertureDeLaFiche couvertureDeLaFiche(Attestations attestations, Identifiants identifiants, HorlogeDesCentres horloge) {
        return new CouvertureDeLaFiche(attestations, identifiants, horloge);
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
    VerrouDuCentre verrouDuCentreDeLaFiche(VerrouDesPlacesDuCentre verrou) {
        return new VerrouDuCentreParLaPlanification(verrou);
    }

    @Bean
    AdmettreUnPatient admettreUnPatient(SessionCourante session, HorlogeDesCentres horloge, Identifiants identifiants,
                                        PlaceDeLaFiche place, PatientRepository patients, MouvementsDesPatients mouvements,
                                        CouvertureDeLaFiche couverture, UniteDeTravail unite, VerrouDuCentre verrou,
                                        PrincipalDeLaFiche principal, PecDeLaFiche pecDeLaFiche) {
        return new AdmettreUnPatient(session, horloge, identifiants, place, patients, mouvements, couverture, unite, verrou,
                principal, pecDeLaFiche);
    }

    @Bean
    PrisesEnCharge prisesEnCharge(JdbcTemplate jdbc) {
        return new PrisesEnChargeJdbc(jdbc);
    }

    @Bean
    AttestationsJdbc attestationsDeLaFiche(JdbcTemplate jdbc) {
        return new AttestationsJdbc(jdbc);
    }

    @Bean
    LectureDesAttestations lectureDesAttestations(JdbcTemplate jdbc) {
        return new LectureDesAttestationsJdbc(jdbc);
    }

    @Bean
    GererLesAttestations gererLesAttestations(SessionCourante session, HorlogeDesCentres horloge, Identifiants identifiants,
                                              Attestations attestations, PatientRepository patients, UniteDeTravail unite,
                                              VerrouDuCentre verrou) {
        return new GererLesAttestations(session, horloge, identifiants, attestations, patients, unite, verrou);
    }

    @Bean
    RechercherLesAttestations rechercherLesAttestations(SessionCourante session, LectureDesAttestations lecture) {
        return new RechercherLesAttestations(session, lecture);
    }

    @Bean
    ListesDAttestationsEnCache listesDAttestationsEnCache(GererLesAttestations gerer) {
        return new ListesDAttestationsEnCache(gerer);
    }

    @Bean
    LectureDesPec lectureDesPec(JdbcTemplate jdbc) {
        return new LectureDesPecJdbc(jdbc);
    }

    @Bean
    PecDeLaFiche pecDeLaFiche(PrisesEnCharge pecs, Identifiants identifiants, HorlogeDesCentres horloge) {
        return new PecDeLaFiche(pecs, identifiants, horloge);
    }

    @Bean
    GererLesPec gererLesPec(SessionCourante session, HorlogeDesCentres horloge, Identifiants identifiants, PrisesEnCharge pecs,
                            PatientRepository patients, AttestationsDuPatient attestations, UniteDeTravail unite,
                            VerrouDuCentre verrou) {
        return new GererLesPec(session, horloge, identifiants, pecs, patients, attestations, unite, verrou);
    }

    @Bean
    RechercherLesPec rechercherLesPec(SessionCourante session, LectureDesPec lecture) {
        return new RechercherLesPec(session, lecture);
    }

    @Bean
    ListesDePecEnCache listesDePecEnCache(GererLesPec gerer) {
        return new ListesDePecEnCache(gerer);
    }

    @Bean
    PrincipalDeLaFiche principalDeLaFiche(AssuresDuCentre assures) {
        return new PrincipalDeLaFiche(assures);
    }

    @Bean
    ModifierLaFiche modifierLaFiche(SessionCourante session, HorlogeDesCentres horloge, Identifiants identifiants,
                                    PlaceDeLaFiche place, PatientRepository patients, MouvementsDesPatients mouvements,
                                    CouvertureDeLaFiche couverture, UniteDeTravail unite, VerrouDuCentre verrou,
                                    PrincipalDeLaFiche principal, PecDeLaFiche pecDeLaFiche) {
        return new ModifierLaFiche(session, horloge, identifiants, place, patients, mouvements, couverture, unite, verrou,
                principal, pecDeLaFiche);
    }

    @Bean
    ConsulterLaFiche consulterLaFiche(SessionCourante session, PatientRepository patients, AssuresDuCentre assures,
                                      GenerateursDuCentre generateurs) {
        return new ConsulterLaFiche(session, patients, assures, generateurs);
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
                                    AssuresDuCentre assures, UniteDeTravail unite, VerrouDuCentre verrou) {
        return new GererLesAssures(session, horloge, patients, assures, unite, verrou);
    }
}
