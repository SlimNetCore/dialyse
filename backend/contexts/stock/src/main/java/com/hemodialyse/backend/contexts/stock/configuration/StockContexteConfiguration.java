package com.hemodialyse.backend.contexts.stock.configuration;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.referentiel.ReferentielDesArticles;
import com.hemodialyse.backend.contexts.stock.application.referentiel.ReferentielDesFournisseursEtEmplacements;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Articles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ReferentielSimple.Emplacements;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ReferentielSimple.Fournisseurs;
import com.hemodialyse.backend.contexts.stock.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.ArticlesJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.EmplacementsJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.FournisseursJdbc;
import com.hemodialyse.backend.contexts.stock.interfaces.catalogue.CatalogueDArticlesDuReferentiel;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.util.UUID;

/**
 * Composition root du contexte Stock : seul endroit qui connaît toutes les couches. Slice S1 : référentiel (articles,
 * fournisseurs, emplacements) et catalogue publié. Les beans sont préfixés {@code stock} (un seul contexte Spring avec le legacy
 * et les autres contextes) ; l'horloge n'est pas un bean.
 */
@Configuration(proxyBeanMethods = false)
public class StockContexteConfiguration {

    @Bean
    SessionCourante stockSessionCourante() {
        return new SessionLegacy();
    }

    @Bean
    Articles stockArticles(JdbcTemplate jdbc) {
        return new ArticlesJdbc(jdbc);
    }

    @Bean
    Fournisseurs stockFournisseurs(JdbcTemplate jdbc) {
        return new FournisseursJdbc(jdbc);
    }

    @Bean
    Emplacements stockEmplacements(JdbcTemplate jdbc) {
        return new EmplacementsJdbc(jdbc);
    }

    @Bean
    ReferentielDesArticles stockReferentielDesArticles(Articles articles) {
        return new ReferentielDesArticles(articles, UUID::randomUUID, Clock.systemDefaultZone());
    }

    @Bean
    ReferentielDesFournisseursEtEmplacements stockReferentielDesFournisseursEtEmplacements(Fournisseurs fournisseurs,
                                                                                           Emplacements emplacements) {
        return new ReferentielDesFournisseursEtEmplacements(fournisseurs, emplacements, UUID::randomUUID);
    }

    @Bean
    CatalogueDArticles stockCatalogueDArticles(ReferentielDesArticles referentiel) {
        return new CatalogueDArticlesDuReferentiel(referentiel);
    }
}

