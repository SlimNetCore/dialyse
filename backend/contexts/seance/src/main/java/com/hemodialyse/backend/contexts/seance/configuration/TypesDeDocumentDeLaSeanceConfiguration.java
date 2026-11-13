package com.hemodialyse.backend.contexts.seance.configuration;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.documents.TypesDeDocumentDeLaSeance;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** Le type de document de la Séance, déclaré à Documents (DOC4c, ADR 0166) ; 13e dans l'ordre des types proposés. */
@Configuration(proxyBeanMethods = false)
public class TypesDeDocumentDeLaSeanceConfiguration {

    @Bean
    @Order(70)
    DeclarationDesTypesDeDocument seanceTypesDeDocument() {
        return new TypesDeDocumentDeLaSeance();
    }
}
