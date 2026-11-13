package com.hemodialyse.backend.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires pure domain services (no Spring/JPA dependency) as Spring beans.
 * <p>
 * Keeping the {@code @Service} / {@code @Transactional} annotations out of the
 * {@code domain/} package enforces the hexagonal boundary (AGENTS.md §3 &amp; §4):
 * high-level use cases stay framework-agnostic and are assembled here, in the
 * infrastructure layer. Transaction atomicity is provided by the persistence
 * adapters (Spring Data repositories are transactional by default).
 * <p>
 * SE7f (ADR 0100) : le bean {@code VoletParamedicalDomainService} est retiré avec le module {@code bc-seance} ; la saisie du volet
 * paramédical vit dans {@code contexts/seance}. Seul reste le service du référentiel d'administration.
 */
@Configuration
public class DomainServiceConfig {

    /** RF1 (ADR 0122) : les transporteurs sont gérés par patient-couverture ; l'administration générique lui délègue. */
    @Bean
    public com.hemodialyse.backend.infrastructure.acl.patientcouverture.TransporteursParPatientCouverture transporteursParPatientCouverture(
            com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs transporteurs) {
        return new com.hemodialyse.backend.infrastructure.acl.patientcouverture.TransporteursParPatientCouverture(transporteurs);
    }

    /** RF2 (ADR 0124) : le catalogue de forfaits est géré par patient-couverture ; l'administration générique lui délègue. */
    @Bean
    public com.hemodialyse.backend.infrastructure.acl.patientcouverture.ForfaitsParPatientCouverture forfaitsParPatientCouverture(
            com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits forfaits) {
        return new com.hemodialyse.backend.infrastructure.acl.patientcouverture.ForfaitsParPatientCouverture(forfaits);
    }

    /** RF3a (ADR 0126) : la chaîne des payeurs (caisses, agences, centres payeurs) est gérée par patient-couverture. */
    @Bean
    public com.hemodialyse.backend.infrastructure.acl.patientcouverture.PayeursParPatientCouverture payeursParPatientCouverture(
            com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs payeurs) {
        return new com.hemodialyse.backend.infrastructure.acl.patientcouverture.PayeursParPatientCouverture(payeurs);
    }

    /** PL11-1 (ADR 0157) : les fournisseurs de descripteurs et de gestion que la façade distribue. */
    @Bean
    public com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminDelegates referentialAdminDelegates(
            com.hemodialyse.backend.infrastructure.acl.patientcouverture.TransporteursParPatientCouverture transporteurs,
            com.hemodialyse.backend.infrastructure.acl.patientcouverture.ForfaitsParPatientCouverture forfaits,
            com.hemodialyse.backend.infrastructure.acl.patientcouverture.PayeursParPatientCouverture payeurs) {
        return new com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminDelegates(
                java.util.List.of(transporteurs, forfaits, payeurs));
    }

    @Bean
    public com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialCatalogue referentialCatalogue(
            com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminDelegates delegues) {
        return new com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialCatalogue(java.util.List.of(delegues));
    }

    @Bean
    public com.hemodialyse.backend.infrastructure.referential.admin.service.ReferentialAdminDomainService referentialAdminDomainService(
            com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminRepositoryPort repo,
            com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminDelegates delegues) {
        return new com.hemodialyse.backend.infrastructure.referential.admin.service.ReferentialAdminDomainService(repo, delegues);
    }
}
