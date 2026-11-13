package com.hemodialyse.backend.contexts.pilotagedirection.configuration;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.documents.TypesDeDocumentDuPilotageDeLaDirection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** Le type de document du Pilotage de la direction, déclaré à Documents (DOC4c, ADR 0166) ; 14e et dernier dans l'ordre des types proposés. */
@Configuration(proxyBeanMethods = false)
public class TypesDeDocumentDuPilotageConfiguration {

    @Bean
    @Order(80)
    DeclarationDesTypesDeDocument pilotageTypesDeDocument() {
        return new TypesDeDocumentDuPilotageDeLaDirection();
    }
}
