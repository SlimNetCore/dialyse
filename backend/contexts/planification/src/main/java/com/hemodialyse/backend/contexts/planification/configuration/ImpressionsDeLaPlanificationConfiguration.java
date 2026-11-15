package com.hemodialyse.backend.contexts.planification.configuration;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;
import com.hemodialyse.backend.contexts.planification.application.CalendriersDesPropositions;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.documents.CalendrierDeLaPropositionImprimeParDocuments;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.documents.PresenceImprimeeParDocuments;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDeLaPresence;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDuCalendrierDeLaProposition;
import com.hemodialyse.backend.contexts.planification.application.PlanningDePresenceImprimable;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.LibellesDeLOrganisation;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation.PlanningDePresenceImprimableAvecLesLibellesPublies;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.RoulementImprimableDeLaSemaineJdbc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Les impressions de la Planification passent par le port publié de Documents (PLN-DOC, ADR 0167) ; elles remplacent les ACL de la plateforme. */
@Configuration(proxyBeanMethods = false)
public class ImpressionsDeLaPlanificationConfiguration {

    @Bean
    PlanningDePresenceImprimable planificationPlanningDePresenceImprimable(JdbcTemplate jdbc, LibellesDeLOrganisation organisation) {
        return new PlanningDePresenceImprimableAvecLesLibellesPublies(new RoulementImprimableDeLaSemaineJdbc(jdbc), organisation);
    }

    @Bean
    ImpressionDeLaPresence planificationImpressionDeLaPresence(RenduDeDocument documents, PlanningDePresenceImprimable planning) {
        return new PresenceImprimeeParDocuments(documents, planning);
    }

    @Bean
    ImpressionDuCalendrierDeLaProposition planificationImpressionDuCalendrierDeLaProposition(RenduDeDocument documents,
                                                                                              CalendriersDesPropositions calendriers) {
        return new CalendrierDeLaPropositionImprimeParDocuments(documents, calendriers);
    }
}
