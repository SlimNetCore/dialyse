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
    public com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminDelegate transporteursParPatientCouverture(
            com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs transporteurs) {
        return new com.hemodialyse.backend.infrastructure.acl.patientcouverture.TransporteursParPatientCouverture(transporteurs);
    }

    @Bean
    public com.hemodialyse.backend.infrastructure.referential.admin.service.ReferentialAdminDomainService referentialAdminDomainService(
            com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminRepositoryPort repo,
            com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminDelegate delegate) {
        return new com.hemodialyse.backend.infrastructure.referential.admin.service.ReferentialAdminDomainService(repo, delegate);
    }
}
