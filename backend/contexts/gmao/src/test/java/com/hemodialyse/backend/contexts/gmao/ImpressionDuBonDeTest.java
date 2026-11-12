package com.hemodialyse.backend.contexts.gmao;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Racine de test : le rendu des documents est fourni par Documents dans l'application (documents.v1) ; sans lui (tests du module
 * seul), le bon est un document vide.
 */
@Configuration(proxyBeanMethods = false)
class ImpressionDuBonDeTest {

    @Bean
    RenduDeDocument renduDeDocument() {
        return (centre, type, donnees, format) -> new DocumentRendu(new byte[]{'%', 'P', 'D', 'F', '-'}, "application/pdf", "bon.pdf");
    }
}
