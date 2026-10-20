package com.hemodialyse.backend.contexts.facturation.configuration;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ParametrageFiscal;
import com.hemodialyse.backend.contexts.facturation.application.SessionCourante;
import com.hemodialyse.backend.contexts.facturation.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.facturation.application.parametres.ConfigurerLaFacturation;
import com.hemodialyse.backend.contexts.facturation.application.simulation.AbsencesDesPatients;
import com.hemodialyse.backend.contexts.facturation.application.simulation.ChangerLeForfaitDUneSeance;
import com.hemodialyse.backend.contexts.facturation.application.simulation.ExclureUneSeance;
import com.hemodialyse.backend.contexts.facturation.application.simulation.ReinclureUneSeance;
import com.hemodialyse.backend.contexts.facturation.application.simulation.SimulerLaFacturation;
import com.hemodialyse.backend.contexts.facturation.application.simulation.TauxDeTvaApplicable;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturationRepository;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeancesDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeancesFacturables;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SequenceDeFacturation;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.comptabilite.TauxDeTvaComptabilite;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.legacyseance.ForfaitsDuCentreJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.legacyseance.SeancesDeFacturationJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.legacyseance.SeancesFacturablesJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.ParametresDeFacturationJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.SequenceDeFacturationJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.transaction.UniteDeTravailSpring;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Composition root du contexte Facturation : seul endroit qui connaît toutes les couches. Slice F1 : paramètres de
 * facturation du centre ; slice F2 : simulation de facturation. Les noms de beans sont préfixés {@code facturation} (un seul
 * contexte Spring avec le legacy et les autres contextes) ; l'horloge n'est pas un bean, pour ne pas concurrencer celle de la
 * Comptabilité. {@link AbsencesDesPatients} est fourni par l'ACL du module Absences (platform).
 */
@Configuration(proxyBeanMethods = false)
public class FacturationContexteConfiguration {

    @Bean
    SessionCourante facturationSessionCourante() {
        return new SessionLegacy();
    }

    @Bean
    UniteDeTravail facturationUniteDeTravail(PlatformTransactionManager transactionManager) {
        return new UniteDeTravailSpring(new TransactionTemplate(transactionManager));
    }

    @Bean
    ParametresDeFacturationRepository facturationParametres(JdbcTemplate jdbc) {
        return new ParametresDeFacturationJdbc(jdbc);
    }

    @Bean
    ConfigurerLaFacturation facturationConfigurerLaFacturation(ParametresDeFacturationRepository parametres,
                                                               UniteDeTravail uniteDeTravail) {
        return new ConfigurerLaFacturation(parametres, uniteDeTravail, Clock.systemUTC());
    }

    @Bean
    SeancesFacturables facturationSeancesFacturables(JdbcTemplate jdbc) {
        return new SeancesFacturablesJdbc(jdbc);
    }

    @Bean
    SeancesDeFacturation facturationSeancesDeFacturation(JdbcTemplate jdbc) {
        return new SeancesDeFacturationJdbc(jdbc);
    }

    @Bean
    ForfaitsDuCentre facturationForfaitsDuCentre(JdbcTemplate jdbc) {
        return new ForfaitsDuCentreJdbc(jdbc);
    }

    @Bean
    SequenceDeFacturation facturationSequence(JdbcTemplate jdbc) {
        return new SequenceDeFacturationJdbc(jdbc);
    }

    @Bean
    TauxDeTvaApplicable facturationTauxDeTva(ParametrageFiscal parametrageFiscal) {
        return new TauxDeTvaComptabilite(parametrageFiscal);
    }

    @Bean
    SimulerLaFacturation facturationSimulerLaFacturation(SeancesFacturables seances, ParametresDeFacturationRepository parametres,
                                                         SequenceDeFacturation sequence, TauxDeTvaApplicable tva,
                                                         UniteDeTravail uniteDeTravail) {
        return new SimulerLaFacturation(seances, parametres, sequence, tva, uniteDeTravail, Clock.systemUTC());
    }

    @Bean
    ExclureUneSeance facturationExclureUneSeance(SeancesDeFacturation seances, AbsencesDesPatients absences,
                                                 SimulerLaFacturation simulation, UniteDeTravail uniteDeTravail) {
        return new ExclureUneSeance(seances, absences, simulation, uniteDeTravail);
    }

    @Bean
    ReinclureUneSeance facturationReinclureUneSeance(SeancesDeFacturation seances, AbsencesDesPatients absences,
                                                     SimulerLaFacturation simulation, UniteDeTravail uniteDeTravail) {
        return new ReinclureUneSeance(seances, absences, simulation, uniteDeTravail);
    }

    @Bean
    ChangerLeForfaitDUneSeance facturationChangerLeForfait(SeancesDeFacturation seances, ForfaitsDuCentre forfaits,
                                                           SimulerLaFacturation simulation, UniteDeTravail uniteDeTravail) {
        return new ChangerLeForfaitDUneSeance(seances, forfaits, simulation, uniteDeTravail, Clock.systemUTC());
    }
}
