package com.hemodialyse.backend.infrastructure.config;

import com.hemodialyse.backend.application.license.LicenseService;
import com.hemodialyse.backend.infrastructure.persistence.entity.CenterJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.CenterJpaRepository;
import com.hemodialyse.backend.infrastructure.security.license.LicenseKeyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Local/dev convenience only: when this instance is running with the ephemeral,
 * in-memory dev keypair (see {@link LicenseKeyProperties}), automatically issues a
 * generous default license for every center that doesn't have one yet, so the app is
 * usable out of the box without a manual trip through the SUPERADMIN licensing UI.
 *
 * <p>This NEVER runs against a real deployment: as soon as real keys are configured
 * (any production setup), {@code isEphemeralDevMode()} is false and this is a no-op —
 * an unlicensed center stays correctly blocked.
 */
@Component
public class DevLicenseBootstrapInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevLicenseBootstrapInitializer.class);

    private final LicenseKeyProperties keyProperties;
    private final CenterJpaRepository centerRepository;
    private final LicenseService licenseService;

    public DevLicenseBootstrapInitializer(LicenseKeyProperties keyProperties,
                                          CenterJpaRepository centerRepository,
                                          LicenseService licenseService) {
        this.keyProperties = keyProperties;
        this.centerRepository = centerRepository;
        this.licenseService = licenseService;
    }

    @Override
    public void run(String... args) {
        if (!keyProperties.isEphemeralDevMode()) {
            return;
        }

        for (CenterJpaEntity center : centerRepository.findAll()) {
            // La clé éphémère change à chaque démarrage : une licence émise lors d'un run précédent
            // existe encore en base mais sa signature ne se vérifie plus. On ne la conserve que si elle
            // est réellement valide (ou révoquée volontairement) ; sinon on en réémet une.
            LicenseService.LicenseVerdict verdict = licenseService.verify(center.getId());
            boolean revoked = verdict.entity() != null
                    && LicenseService.STATUS_REVOKED.equals(verdict.entity().getStatus());
            if (verdict.valid() || revoked) {
                continue;
            }
            licenseService.issue(
                    center.getId(), "STANDARD", 50,
                    Instant.now(), Instant.now().plus(365, ChronoUnit.DAYS),
                    null
            );
            log.warn("[DEV] Licence de développement auto-émise pour le centre '{}' (clé éphémère, non valable en production).",
                    center.getName());
        }
    }
}
