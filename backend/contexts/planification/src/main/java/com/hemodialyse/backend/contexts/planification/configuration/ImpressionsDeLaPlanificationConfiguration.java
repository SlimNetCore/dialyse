package com.hemodialyse.backend.contexts.planification.configuration;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;
import com.hemodialyse.backend.contexts.planification.application.CalendriersDesPropositions;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.documents.CalendrierDeLaPropositionImprimeParDocuments;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.documents.PresenceImprimeeParDocuments;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDeLaPresence;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDuCalendrierDeLaProposition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Les impressions de la Planification passent par le port publié de Documents (PLN-DOC, ADR 0167) ; elles remplacent les ACL de la plateforme. */
@Configuration(proxyBeanMethods = false)
public class ImpressionsDeLaPlanificationConfiguration {

    @Bean
    ImpressionDeLaPresence planificationImpressionDeLaPresence(RenduDeDocument documents) {
        return new PresenceImprimeeParDocuments(documents);
    }

    @Bean
    ImpressionDuCalendrierDeLaProposition planificationImpressionDuCalendrierDeLaProposition(RenduDeDocument documents,
                                                                                              CalendriersDesPropositions calendriers) {
        return new CalendrierDeLaPropositionImprimeParDocuments(documents, calendriers);
    }
}
