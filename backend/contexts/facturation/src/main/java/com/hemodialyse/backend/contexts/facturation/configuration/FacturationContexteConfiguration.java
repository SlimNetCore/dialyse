package com.hemodialyse.backend.contexts.facturation.configuration;

import com.hemodialyse.backend.contexts.facturation.application.SessionCourante;
import com.hemodialyse.backend.contexts.facturation.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.facturation.application.parametres.ConfigurerLaFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturationRepository;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.ParametresDeFacturationJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.transaction.UniteDeTravailSpring;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Composition root du contexte Facturation : seul endroit qui connaît toutes les couches. Slice F1 : paramètres de
 * facturation du centre. Les noms de beans sont préfixés {@code facturation} (un seul contexte Spring avec le legacy et les
 * autres contextes) ; l'horloge n'est pas un bean, pour ne pas concurrencer celle de la Comptabilité.
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
}
