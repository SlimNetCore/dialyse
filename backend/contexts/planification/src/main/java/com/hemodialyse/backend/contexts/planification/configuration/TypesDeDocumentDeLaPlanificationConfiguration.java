package com.hemodialyse.backend.contexts.planification.configuration;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.TypesDeDocumentDeLaPlanification;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** Les types de document de la Planification, déclarés à Documents (DOC4c, ADR 0166) ; 11e et 12e dans l'ordre des types proposés. */
@Configuration(proxyBeanMethods = false)
public class TypesDeDocumentDeLaPlanificationConfiguration {

    @Bean
    @Order(60)
    DeclarationDesTypesDeDocument planificationTypesDeDocument() {
        return TypesDeDocumentDeLaPlanification::tous;
    }
}
