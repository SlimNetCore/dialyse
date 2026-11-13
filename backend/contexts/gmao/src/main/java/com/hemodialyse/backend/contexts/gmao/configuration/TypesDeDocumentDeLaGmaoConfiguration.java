package com.hemodialyse.backend.contexts.gmao.configuration;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.gmao.infrastructure.acl.documents.TypesDeDocumentDeLaGmao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** Le type de document de la GMAO, déclaré à Documents (DOC4c, ADR 0166) ; 10e dans l'ordre des types proposés. */
@Configuration(proxyBeanMethods = false)
public class TypesDeDocumentDeLaGmaoConfiguration {

    @Bean
    @Order(50)
    DeclarationDesTypesDeDocument gmaoTypesDeDocument() {
        return new TypesDeDocumentDeLaGmao();
    }
}
