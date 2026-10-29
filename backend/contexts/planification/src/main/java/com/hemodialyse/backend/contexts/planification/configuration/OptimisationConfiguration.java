package com.hemodialyse.backend.contexts.planification.configuration;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.IndisponibilitesDesGenerateurs;
import com.hemodialyse.backend.contexts.planification.application.AppliquerUneProposition;
import com.hemodialyse.backend.contexts.planification.application.ArreterUneOptimisation;
import com.hemodialyse.backend.contexts.planification.application.CalendriersDesPropositions;
import com.hemodialyse.backend.contexts.planification.application.CentresActifs;
import com.hemodialyse.backend.contexts.planification.application.ConsulterLesOptimisations;
import com.hemodialyse.backend.contexts.planification.application.ContraintesDesPatients;
import com.hemodialyse.backend.contexts.planification.application.DonneesDeLOptimisation;
import com.hemodialyse.backend.contexts.planification.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.planification.application.ImprimerLeCalendrierDeLaProposition;
import com.hemodialyse.backend.contexts.planification.application.IndisponibilitesDeGenerateurs;
import com.hemodialyse.backend.contexts.planification.application.LancerUneOptimisation;
import com.hemodialyse.backend.contexts.planification.application.LectureDesRessources;
import com.hemodialyse.backend.contexts.planification.application.LireLesDonneesDeLOptimisation;
import com.hemodialyse.backend.contexts.planification.application.NotificationsDeLOptimisation;
import com.hemodialyse.backend.contexts.planification.application.NouveauxIdentifiantsDeLOptimisation;
import com.hemodialyse.backend.contexts.planification.application.Optimiseur;
import com.hemodialyse.backend.contexts.planification.application.PersonnelDeLaPresence;
import com.hemodialyse.backend.contexts.planification.application.ReplanifierLaNuit;
import com.hemodialyse.backend.contexts.planification.application.ReprendreLesExecutionsInterrompues;
import com.hemodialyse.backend.contexts.planification.application.SeancesRealisees;
import com.hemodialyse.backend.contexts.planification.application.SejoursDesPatients;
import com.hemodialyse.backend.contexts.planification.application.SupprimerUneExecution;
import com.hemodialyse.backend.contexts.planification.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.planification.application.VerrouillerLesFichesDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.domain.DeplacementsTemporairesRepository;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionsRepository;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository;
import com.hemodialyse.backend.contexts.planification.domain.PreferencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.ProfilsRepository;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesRepository;
import com.hemodialyse.backend.contexts.planification.domain.RemplacementsRepository;
import com.hemodialyse.backend.contexts.planification.domain.RoulementsRepository;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacygmao.IndisponibilitesDeGenerateursGmao;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacynotifications.NotificationsDeLOptimisationLegacy;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient.ContraintesDesPatientsJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.motdepasse.NouveauxIdentifiantsDeLOptimisationAleatoires;
import com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.TimefoldOptimiseurAdapter;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.CalendriersDesPropositionsJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.DeplacementsTemporairesJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.ExecutionsJdbc;
import com.hemodialyse.backend.contexts.planification.interfaces.jobs.ReplanificationDe0230;
import com.hemodialyse.backend.contexts.planification.interfaces.jobs.RepriseDesExecutionsAuDemarrage;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.AlertesDeLOptimisation;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CachesDesPatients;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDuCalendrierDeLaProposition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/**
 * Composition root de la slice P8 (optimisation du planning) : exécutions d'optimisation, déplacements temporaires, application d'une
 * proposition, replanification de nuit, solveur Timefold derrière le port {@code Optimiseur} (ADR 0050). Le legacy d'optimisation a
 * été retiré : ce module est désormais le seul chemin.
 */
@Configuration(proxyBeanMethods = false)
public class OptimisationConfiguration {

    @Bean
    ExecutionsRepository planificationExecutions(JdbcTemplate jdbc, ObjectMapper mapper) {
        // un jeton par démarrage : seule cette instance fait avancer les calculs qu'elle a lancés
        return new ExecutionsJdbc(jdbc, mapper, java.util.UUID.randomUUID().toString());
    }

    @Bean
    CalendriersDesPropositions planificationCalendriersDesPropositions(JdbcTemplate jdbc, ObjectMapper mapper) {
        return new CalendriersDesPropositionsJdbc(jdbc, mapper);
    }

    @Bean
    DeplacementsTemporairesRepository planificationDeplacementsTemporairesRepository(JdbcTemplate jdbc) {
        return new DeplacementsTemporairesJdbc(jdbc);
    }

    @Bean
    ContraintesDesPatients planificationContraintesDesPatients(JdbcTemplate jdbc) {
        return new ContraintesDesPatientsJdbc(jdbc);
    }

    @Bean
    IndisponibilitesDeGenerateurs planificationIndisponibilitesDeGenerateurs(IndisponibilitesDesGenerateurs gmao) {
        return new IndisponibilitesDeGenerateursGmao(gmao);
    }

    @Bean
    NotificationsDeLOptimisation planificationNotificationsDeLOptimisation(AlertesDeLOptimisation alertes,
                                                                           com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository infirmiers) {
        return new NotificationsDeLOptimisationLegacy(alertes, infirmiers);
    }

    @Bean
    NouveauxIdentifiantsDeLOptimisation planificationNouveauxIdentifiantsDeLOptimisation() {
        return new NouveauxIdentifiantsDeLOptimisationAleatoires();
    }

    @Bean
    Optimiseur planificationOptimiseur(@Value("${app.planning.optimisation.workers:2}") int workers) {
        return new TimefoldOptimiseurAdapter(workers);
    }

    @Bean
    DonneesDeLOptimisation planificationDonneesDeLOptimisation(LectureDesRessources ressources, PersonnelDeLaPresence personnel,
                                                               SejoursDesPatients patients, PreferencesRepository preferences,
                                                               ProfilsRepository profils, ContraintesDesPatients contraintes,
                                                               IndisponibilitesDeGenerateurs indisponibilites,
                                                               DeplacementsTemporairesRepository temporaires, SeancesRealisees seances) {
        return new LireLesDonneesDeLOptimisation(ressources, personnel, patients, preferences, profils, contraintes, indisponibilites,
                temporaires, seances);
    }

    @Bean
    LancerUneOptimisation planificationLancerUneOptimisation(UniteDeTravail unite, VerrouDesPlaces verrou, ReglagesRepository reglages,
                                                             ExecutionsRepository executions, DonneesDeLOptimisation donnees,
                                                             CalendriersDesPropositions calendriers, Optimiseur optimiseur,
                                                             NouveauxIdentifiantsDeLOptimisation identifiants) {
        return new LancerUneOptimisation(unite, verrou, reglages, executions, donnees, calendriers, optimiseur, identifiants,
                Clock.systemUTC());
    }

    @Bean
    ConsulterLesOptimisations planificationConsulterLesOptimisations(ExecutionsRepository executions,
                                                                     CalendriersDesPropositions calendriers) {
        return new ConsulterLesOptimisations(executions, calendriers);
    }

    @Bean
    ArreterUneOptimisation planificationArreterUneOptimisation(ExecutionsRepository executions, Optimiseur optimiseur) {
        return new ArreterUneOptimisation(executions, optimiseur);
    }

    @Bean
    SupprimerUneExecution planificationSupprimerUneExecution(UniteDeTravail unite, VerrouDesPlaces verrou,
                                                             ExecutionsRepository executions,
                                                             CalendriersDesPropositions calendriers) {
        return new SupprimerUneExecution(unite, verrou, executions, calendriers);
    }

    @Bean
    ReprendreLesExecutionsInterrompues planificationReprendreLesExecutionsInterrompues(UniteDeTravail unite,
                                                                                       ExecutionsRepository executions) {
        return new ReprendreLesExecutionsInterrompues(unite, executions, Clock.systemUTC());
    }

    @Bean
    AppliquerUneProposition planificationAppliquerUneProposition(UniteDeTravail unite, VerrouDesPlaces verrou,
                                                                 VerrouillerLesFichesDesInfirmiers fiches, ExecutionsRepository executions,
                                                                 DonneesDeLOptimisation donnees, PlacesDuCentreRepository places,
                                                                 HorlogeDesCentres horloge, RoulementsRepository roulements,
                                                                 RemplacementsRepository remplacements,
                                                                 DeplacementsTemporairesRepository deplacements,
                                                                 NouveauxIdentifiantsDeLOptimisation identifiants,
                                                                 NotificationsDeLOptimisation notifications, CachesDesPatients caches) {
        return new AppliquerUneProposition(unite, verrou, fiches, executions, donnees, places, horloge, roulements, remplacements,
                deplacements, identifiants, notifications, caches, Clock.systemUTC());
    }

    @Bean
    ImprimerLeCalendrierDeLaProposition planificationImprimerLeCalendrierDeLaProposition(ExecutionsRepository executions,
                                                                                         CalendriersDesPropositions calendriers,
                                                                                         ImpressionDuCalendrierDeLaProposition impression) {
        return new ImprimerLeCalendrierDeLaProposition(executions, calendriers, impression);
    }

    @Bean
    ReplanifierLaNuit planificationReplanifierLaNuit(ReglagesRepository reglages, HorlogeDesCentres horloge,
                                                     LancerUneOptimisation lancer, PersonnelDeLaPresence personnel,
                                                     NotificationsDeLOptimisation notifications) {
        return new ReplanifierLaNuit(reglages, horloge, lancer, personnel, notifications);
    }

    @Bean
    ReplanificationDe0230 planificationReplanificationDe0230(ReplanifierLaNuit replanifier) {
        return new ReplanificationDe0230(replanifier);
    }

    @Bean
    RepriseDesExecutionsAuDemarrage planificationRepriseDesExecutionsAuDemarrage(ReprendreLesExecutionsInterrompues reprendre) {
        return new RepriseDesExecutionsAuDemarrage(reprendre);
    }
}
