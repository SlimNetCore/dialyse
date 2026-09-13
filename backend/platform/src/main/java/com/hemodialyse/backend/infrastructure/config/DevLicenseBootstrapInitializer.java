package com.hemodialyse.backend.infrastructure.config;

import com.hemodialyse.backend.application.license.LicenseService;
import com.hemodialyse.backend.infrastructure.persistence.entity.CenterJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.CenterJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.LicenseJpaRepository;
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
    private final LicenseJpaRepository licenseRepository;
    private final LicenseService licenseService;

    public DevLicenseBootstrapInitializer(LicenseKeyProperties keyProperties,
                                          CenterJpaRepository centerRepository,
                                          LicenseJpaRepository licenseRepository,
                                          LicenseService licenseService) {
        this.keyProperties = keyProperties;
        this.centerRepository = centerRepository;
        this.licenseRepository = licenseRepository;
        this.licenseService = licenseService;
    }

    @Override
    public void run(String... args) {
        if (!keyProperties.isEphemeralDevMode()) {
            return;
        }

        for (CenterJpaEntity center : centerRepository.findAll()) {
            boolean hasLicense = !licenseRepository.findByCenterIdOrderByCreatedAtDesc(center.getId()).isEmpty();
            if (hasLicense) {
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
