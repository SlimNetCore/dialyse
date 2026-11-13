package com.hemodialyse.backend.contexts.stock.configuration;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.stock.infrastructure.acl.documents.TypesDeDocumentDuStock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** Le type de document du Stock, déclaré à Documents (DOC4c, ADR 0166) ; 9e dans l'ordre des types proposés. */
@Configuration(proxyBeanMethods = false)
public class TypesDeDocumentDuStockConfiguration {

    @Bean
    @Order(40)
    DeclarationDesTypesDeDocument stockTypesDeDocument() {
        return new TypesDeDocumentDuStock();
    }
}
