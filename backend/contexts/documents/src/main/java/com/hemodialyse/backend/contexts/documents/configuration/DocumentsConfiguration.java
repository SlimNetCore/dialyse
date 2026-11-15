package com.hemodialyse.backend.contexts.documents.configuration;

import com.hemodialyse.backend.contexts.documents.application.IdentitesImprimables;
import com.hemodialyse.backend.contexts.documents.application.ListerLesTypesDeDocument;
import com.hemodialyse.backend.contexts.documents.infrastructure.declaration.TypesLivresDeDocuments;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
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
import com.hemodialyse.backend.contexts.documents.application.CatalogueDesTypesLivres;
import com.hemodialyse.backend.contexts.documents.application.ConnexionDeRendu;
import com.hemodialyse.backend.contexts.documents.infrastructure.rendu.ConnexionDeRenduDeLaBase;
import com.hemodialyse.backend.contexts.documents.application.ReglesDImpression;
import com.hemodialyse.backend.contexts.documents.application.ReglesDesTypesDeclares;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RegleDImpressionDUnType;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee;
import com.hemodialyse.backend.contexts.documents.infrastructure.rendu.CustomTemplateCompiler;
import com.hemodialyse.backend.contexts.documents.infrastructure.rendu.JasperReportService;
import com.hemodialyse.backend.contexts.documents.infrastructure.rendu.ModeleDocumentProvisioner;
import com.hemodialyse.backend.contexts.documents.infrastructure.rendu.MoteurDeRenduJasper;
import com.hemodialyse.backend.contexts.documents.application.GabaritPersonnaliseDuCentre;
import com.hemodialyse.backend.contexts.documents.application.GabaritsLivres;
import com.hemodialyse.backend.contexts.documents.application.GererLesGabarits;
import com.hemodialyse.backend.contexts.documents.application.ModelesDuCentre;
import com.hemodialyse.backend.contexts.documents.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.documents.domain.HistoriqueDesVersionsRepository;
import com.hemodialyse.backend.contexts.documents.infrastructure.persistence.HistoriqueDesVersionsJdbc;
import com.hemodialyse.backend.contexts.documents.infrastructure.persistence.ModelesDuCentreJdbc;
import com.hemodialyse.backend.contexts.documents.infrastructure.rendu.GabaritsLivresDuClasspath;
import com.hemodialyse.backend.contexts.documents.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.documents.application.GererLesModeles;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.VerificationDeGabarit;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Composition du contexte Documents (DOC1, ADR 0140, 0142) : le papier en-tête, les routes d'impression {@code /api/v1/documents/print} et
 * le langage publié {@code documents.v1}. Mêmes tables et mêmes URL que la plateforme d'avant ; aucun flag, aucune façade. Le moteur de
 * rendu ({@link MoteurDeRendu}) est celui de ce contexte depuis DOC2 ; les règles des types sensibles ({@link RegleDImpressionDUnType}) sont
 * publiées par les contextes propriétaires de ces types (DOC4c, ADR 0166) et orchestrées ici.
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
    ConnexionDeRendu connexionDeRendu(DataSource dataSource) {
        return new ConnexionDeRenduDeLaBase(dataSource);
    }

    @Bean
    JasperReportService jasperReportService(ConnexionDeRendu connexions) {
        return new JasperReportService(connexions);
    }

    @Bean
    CustomTemplateCompiler customTemplateCompiler() {
        return new CustomTemplateCompiler();
    }

    /** Les types livrés : la réunion des types déclarés par les contextes propriétaires (DOC4c, ADR 0166), dans l'ordre des déclarations. */
    @Bean
    CatalogueDesTypesLivres catalogueDesTypesLivres(java.util.List<DeclarationDesTypesDeDocument> declarations) {
        return new CatalogueDesTypesLivres(declarations);
    }

    @Bean
    ModeleDocumentProvisioner modeleDocumentProvisioner(JdbcTemplate jdbc, AnnuaireDesCentres annuaireDesCentres,
                                                        CatalogueDesTypesLivres catalogue) {
        return new ModeleDocumentProvisioner(jdbc, annuaireDesCentres, catalogue);
    }

    /** L'historique des versions de gabarit (DOC3, ADR 0163) ; il sert aussi le moteur : la version en vigueur d'un modèle. */
    @Bean
    HistoriqueDesVersionsJdbc historiqueDesVersionsDeGabarit(JdbcTemplate jdbc) {
        return new HistoriqueDesVersionsJdbc(jdbc);
    }

    @Bean
    ModelesDuCentre modelesDuCentre(JdbcTemplate jdbc) {
        return new ModelesDuCentreJdbc(jdbc);
    }

    @Bean
    GabaritsLivres gabaritsLivres() {
        return new GabaritsLivresDuClasspath();
    }

    @Bean
    UniteDeTravail uniteDeTravailDesDocuments(PlatformTransactionManager transactionManager) {
        return new UniteDeTravailSpring(new TransactionTemplate(transactionManager));
    }

    @Bean
    GererLesGabarits gererLesGabarits(SessionCourante session, ModelesDuCentre modeles, GabaritsLivres livres,
                                      HistoriqueDesVersionsRepository historiques, VerificationDeGabarit verification,
                                      UniteDeTravail unite) {
        return new GererLesGabarits(session, modeles, livres, historiques, verification, unite, java.time.Clock.systemUTC(),
                java.util.UUID::randomUUID);
    }

    /** La gestion des modèles de document (DOC4a, ADR 0164) ; supprimer un modèle retire aussi l'historique de ses versions de gabarit. */
    @Bean
    GererLesModeles gererLesModeles(SessionCourante session, ModelesDeDocument modeles, GererLesGabarits gabarits, UniteDeTravail unite) {
        return new GererLesModeles(session, modeles, gabarits, unite, java.util.UUID::randomUUID);
    }

    /** Le moteur de rendu (DOC2, ADR 0162) ; les gabarits personnalisés des centres sont ceux de ce contexte depuis DOC3 (ADR 0163). */
    @Bean
    MoteurDeRendu moteurDeRendu(JdbcTemplate jdbc, JasperReportService jasper, GabaritPersonnaliseDuCentre gabarits,
                                CustomTemplateCompiler compiler, PapierEnTeteDuCentre papier, ModeleDocumentProvisioner provisioner,
                                CatalogueDesTypesLivres catalogue) {
        return new MoteurDeRenduJasper(jdbc, jasper, gabarits, compiler, papier, provisioner, catalogue);
    }

    /**
     * Les six documents du patient et de ses listes, déclarés par Documents lui-même (DOC4b, ADR 0165) ; les autres contextes déclarent les
     * leurs par le même port (DOC4c, ADR 0166). L'ordre des déclarations est celui des types proposés : {@code @Order} sur chaque bean.
     */
    @Bean
    @org.springframework.core.annotation.Order(10)
    DeclarationDesTypesDeDocument typesLivresDeDocuments() {
        return new TypesLivresDeDocuments();
    }

    /** Les types de document proposés : l'agrégat des déclarations (DOC4b, ADR 0165). */
    @Bean
    ListerLesTypesDeDocument listerLesTypesDeDocument(java.util.List<DeclarationDesTypesDeDocument> declarations) {
        return new ListerLesTypesDeDocument(declarations);
    }

    /** Les règles d'impression publiées par les contextes propriétaires des types sensibles (DOC4c, ADR 0166), retrouvées par type. */
    @Bean
    ReglesDImpression reglesDImpressionDesTypes(java.util.List<RegleDImpressionDUnType> regles) {
        return new ReglesDesTypesDeclares(regles);
    }

    @Bean
    ImprimerUnDocument imprimerUnDocument(SessionCourante session, ModelesDeDocument modeles, PapierEnTeteDesDocuments papier,
                                          MoteurDeRendu moteur, ReglesDImpression regles) {
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
