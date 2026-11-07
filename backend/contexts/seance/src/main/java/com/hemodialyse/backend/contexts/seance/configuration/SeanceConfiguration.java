package com.hemodialyse.backend.contexts.seance.configuration;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import com.hemodialyse.backend.contexts.seance.application.ConsommationsDeLaSeance;
import com.hemodialyse.backend.contexts.seance.application.EligibiliteALaFacturation;
import com.hemodialyse.backend.contexts.seance.application.GenerateurDIdentifiants;
import com.hemodialyse.backend.contexts.seance.application.HorlogeDuCentre;
import com.hemodialyse.backend.contexts.seance.application.SessionCourante;
import com.hemodialyse.backend.contexts.seance.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture.EligibiliteParLaCouverture;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock.ConsommationsDeLaSeanceDuStock;
import com.hemodialyse.backend.contexts.seance.infrastructure.identifiants.GenerateurDIdentifiantsAleatoires;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.SeancesJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

/**
 * Câblage du contexte Séance (ADR 0088). SESSION A : seuls les adapters qui ne dépendent d'aucun port de la plateforme sont câblés
 * (dépôt JDBC des séances, unité de travail, identifiants, horloge du centre, session, ACL de la couverture et du Stock). Les cas d'usage
 * (créer, scanner, consulter, lister) ne sont PAS exposés : ils attendent les adapters de la plateforme (patients, planning, diffusions,
 * validation, générateurs, volets, forfaits, historique) et le contrôleur, SESSION B. Aucun trafic ne passe par ce module. Les noms de
 * beans portent le suffixe du contexte : les modules voisins ont les mêmes noms de ports.
 */
@Configuration(proxyBeanMethods = false)
public class SeanceConfiguration {

    /** Jour du centre : la table des centres n'a pas de fuseau ; Africa/Algiers par défaut (KB-SEA-01). */
    private static final ZoneId FUSEAU_DU_CENTRE = ZoneId.of("Africa/Algiers");
    private static final Duration DELAI_DE_VERROU = Duration.ofSeconds(5);

    @Bean
    Seances seancesDuContexteSeance(JdbcTemplate jdbc) {
        return new SeancesJdbc(jdbc);
    }

    @Bean
    SessionCourante sessionCouranteDeLaSeance() {
        return new SessionLegacy();
    }

    @Bean
    HorlogeDuCentre horlogeDeLaSeance() {
        return new HorlogeDuCentre(Clock.systemUTC(), FUSEAU_DU_CENTRE);
    }

    @Bean
    UniteDeTravail uniteDeTravailDeLaSeance(PlatformTransactionManager transactionManager, JdbcTemplate jdbc) {
        return new UniteDeTravailSpring(new TransactionTemplate(transactionManager), jdbc, DELAI_DE_VERROU);
    }

    @Bean
    GenerateurDIdentifiants generateurDIdentifiantsDeLaSeance() {
        return new GenerateurDIdentifiantsAleatoires();
    }

    @Bean
    EligibiliteALaFacturation eligibiliteALaFacturationDeLaSeance(CouvertureDesPatients couverture) {
        return new EligibiliteParLaCouverture(couverture);
    }

    @Bean
    ConsommationsDeLaSeance consommationsDeLaSeanceDuStock(ConsommationsDeSeance stock) {
        return new ConsommationsDeLaSeanceDuStock(stock);
    }
}
