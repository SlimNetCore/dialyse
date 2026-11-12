package com.hemodialyse.backend.contexts.documents.configuration;

import com.hemodialyse.backend.contexts.documents.application.IdentitesImprimables;
import com.hemodialyse.backend.contexts.documents.application.ImprimerUnDocument;
import com.hemodialyse.backend.contexts.documents.application.ModelesDeDocument;
import com.hemodialyse.backend.contexts.documents.application.PapierEnTeteDesDocuments;
import com.hemodialyse.backend.contexts.documents.application.RendreUnDocument;
import com.hemodialyse.backend.contexts.documents.application.SessionCourante;
import com.hemodialyse.backend.contexts.documents.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.documents.infrastructure.acl.organisation.IdentitesImprimablesParOrganisation;
import com.hemodialyse.backend.contexts.documents.infrastructure.persistence.ModelesDeDocumentJdbc;
import com.hemodialyse.backend.contexts.documents.interfaces.publie.PapierEnTeteDuCentrePublie;
import com.hemodialyse.backend.contexts.documents.interfaces.publie.RenduDeDocumentPublie;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.MoteurDeRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.PapierEnTeteDuCentre;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.ReglesDImpressionDesTypesSensibles;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Composition du contexte Documents (DOC1, ADR 0140, 0142) : le papier en-tête, les routes d'impression {@code /api/v1/documents/print} et
 * le langage publié {@code documents.v1}. Mêmes tables et mêmes URL que la plateforme d'avant ; aucun flag, aucune façade. Le moteur de
 * rendu ({@link MoteurDeRendu}) et les règles des types sensibles ({@link ReglesDImpressionDesTypesSensibles}) sont fournis par la
 * plateforme jusqu'à DOC2 et DOC4.
 */
@Configuration(proxyBeanMethods = false)
public class DocumentsConfiguration {

    @Bean
    SessionCourante sessionCouranteDesDocuments() {
        return new SessionLegacy();
    }

    @Bean
    IdentitesImprimables identitesImprimablesDesDocuments(IdentiteImprimee organisation) {
        return new IdentitesImprimablesParOrganisation(organisation);
    }

    @Bean
    ModelesDeDocument modelesDeDocument(JdbcTemplate jdbc) {
        return new ModelesDeDocumentJdbc(jdbc);
    }

    @Bean
    PapierEnTeteDesDocuments papierEnTeteDesDocuments(IdentitesImprimables identites) {
        return new PapierEnTeteDesDocuments(identites);
    }

    @Bean
    ImprimerUnDocument imprimerUnDocument(SessionCourante session, ModelesDeDocument modeles, PapierEnTeteDesDocuments papier,
                                          MoteurDeRendu moteur, ReglesDImpressionDesTypesSensibles regles) {
        return new ImprimerUnDocument(session, modeles, papier, moteur, regles);
    }

    @Bean
    RendreUnDocument rendreUnDocument(MoteurDeRendu moteur) {
        return new RendreUnDocument(moteur);
    }

    @Bean
    PapierEnTeteDuCentre papierEnTeteDuCentre(PapierEnTeteDesDocuments papier) {
        return new PapierEnTeteDuCentrePublie(papier);
    }

    @Bean
    RenduDeDocument renduDeDocument(RendreUnDocument rendre) {
        return new RenduDeDocumentPublie(rendre);
    }
}
