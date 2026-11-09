package com.hemodialyse.backend.contexts.organisation.configuration;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.organisation.application.OrganisationService;
import com.hemodialyse.backend.contexts.organisation.application.OrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.application.ReferentielsCommunsService;
import com.hemodialyse.backend.contexts.organisation.application.ReferentielsCommunsUseCases;
import com.hemodialyse.backend.contexts.organisation.domain.ReferentielsCommunsRepository;
import com.hemodialyse.backend.contexts.organisation.application.ReferentielsLectureService;
import com.hemodialyse.backend.contexts.organisation.application.ReferentielsLectureUseCases;
import com.hemodialyse.backend.contexts.organisation.domain.CentreRepository;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauRepository;
import com.hemodialyse.backend.contexts.organisation.domain.SalleRepository;
import com.hemodialyse.backend.contexts.organisation.domain.SocieteRepository;
import com.hemodialyse.backend.contexts.organisation.application.ReferentielsOrganisationService;
import com.hemodialyse.backend.contexts.organisation.application.ReferentielsOrganisationUseCases;
import com.hemodialyse.backend.contexts.organisation.domain.MachineRepository;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinRepository;
import com.hemodialyse.backend.contexts.organisation.domain.ProtocoleRepository;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.CentresJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.CreneauxJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.MachinesJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.MedecinsJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.ProtocolesJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.ReferentielsCommunsJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.SallesJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.SocietesJdbc;
import com.hemodialyse.backend.contexts.organisation.infrastructure.GenerateursDuCentreMisEnCache;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class OrganisationConfiguration {
    @Bean
    public com.hemodialyse.backend.contexts.organisation.application.SessionCourante organisationSessionCourante() {
        return new com.hemodialyse.backend.contexts.organisation.infrastructure.acl.legacyidentite.SessionLegacy();
    }

    @Bean
    public SocieteRepository societeRepository(JdbcTemplate jdbc) {
        return new SocietesJdbc(jdbc);
    }

    @Bean
    public CentreRepository centreRepository(JdbcTemplate jdbc) {
        return new CentresJdbc(jdbc);
    }

    @Bean
    public com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.EvictionApresCommit organisationEviction(
            org.springframework.beans.factory.ObjectProvider<org.springframework.cache.CacheManager> caches) {
        return new com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.EvictionApresCommit(caches.getIfAvailable());
    }

    @Bean
    public SalleRepository salleRepository(JdbcTemplate jdbc,
            com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.EvictionApresCommit eviction) {
        return new SallesJdbc(jdbc, eviction);
    }

    @Bean
    public CreneauRepository creneauRepository(JdbcTemplate jdbc,
            com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.EvictionApresCommit eviction) {
        return new CreneauxJdbc(jdbc, eviction);
    }

    @Bean
    public MedecinRepository medecinRepository(JdbcTemplate jdbc,
            com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.EvictionApresCommit eviction) {
        return new MedecinsJdbc(jdbc, eviction);
    }

    @Bean
    public MachineRepository machineRepository(JdbcTemplate jdbc) {
        return new MachinesJdbc(jdbc);
    }

    @Bean
    public ProtocoleRepository protocoleRepository(JdbcTemplate jdbc) {
        return new ProtocolesJdbc(jdbc);
    }

    @Bean
    public com.hemodialyse.backend.contexts.organisation.application.UniteDeTravail organisationUniteDeTravail(
            org.springframework.transaction.PlatformTransactionManager transactionManager, JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.organisation.infrastructure.transaction.UniteDeTravailSpring(
                new org.springframework.transaction.support.TransactionTemplate(transactionManager), jdbc,
                java.time.Duration.ofSeconds(5));
    }

    @Bean
    public com.hemodialyse.backend.contexts.organisation.domain.VerrouDeLaSociete organisationVerrouDeLaSociete(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.VerrouDeLaSocieteJdbc(jdbc);
    }

    @Bean
    public OrganisationUseCases organisationUseCases(SocieteRepository s, CentreRepository c, SalleRepository sl, CreneauRepository cr,
                                                     com.hemodialyse.backend.contexts.organisation.application.UniteDeTravail unite,
                                                     com.hemodialyse.backend.contexts.organisation.domain.VerrouDeLaSociete verrou) {
        return new OrganisationService(s, c, sl, cr, unite, verrou, java.time.Clock.systemUTC(), java.util.UUID::randomUUID);
    }

    @Bean
    public ReferentielsOrganisationUseCases referentielsOrganisationUseCases(MedecinRepository m, MachineRepository ma, ProtocoleRepository p) {
        return new ReferentielsOrganisationService(m, ma, p, java.util.UUID::randomUUID);
    }

    @Bean
    public GenerateursDuCentre generateursDuCentreMisEnCache(GenerateursDuCentre generateurs) {
        return new GenerateursDuCentreMisEnCache(generateurs);
    }

    @Bean
    public ReferentielsLectureUseCases referentielsLectureUseCases(
            CatalogueDArticles catalogue,
            @org.springframework.beans.factory.annotation.Qualifier("generateursDuCentreMisEnCache") GenerateursDuCentre generateurs) {
        return new ReferentielsLectureService(catalogue, generateurs);
    }

    @Bean
    public ReferentielsCommunsRepository referentielsCommunsRepository(JdbcTemplate jdbc) {
        return new ReferentielsCommunsJdbc(jdbc);
    }

    /** Langage publié organisation.v1 (O4 lot A, ADR 0107) : lecture des centres pour les autres contextes. */
    @Bean
    public com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres annuaireDesCentres(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.AnnuaireDesCentresJdbc(jdbc);
    }

    /** Langage publié organisation.v1 (O4 lot B, ADR 0108) : verrou de la ligne d'un centre, pris dans la transaction de l'appelant. */
    @Bean
    public com.hemodialyse.backend.contexts.organisation.publishedlanguage.VerrouDuCentre verrouDuCentre(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.VerrouDuCentreJdbc(jdbc);
    }

    /** Langage publié organisation.v1 (O4 lot A, ADR 0107) : lecture des sociétés pour les autres contextes. */
    @Bean
    public com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesSocietes annuaireDesSocietes(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.AnnuaireDesSocietesJdbc(jdbc);
    }

    @Bean
    public ReferentielsCommunsUseCases referentielsCommunsUseCases(ReferentielsCommunsRepository referentiels) {
        return new ReferentielsCommunsService(referentiels);
    }
}
