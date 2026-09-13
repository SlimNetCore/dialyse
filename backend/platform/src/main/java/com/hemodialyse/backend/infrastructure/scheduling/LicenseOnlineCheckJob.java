package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.license.LicenseService;
import com.hemodialyse.backend.infrastructure.persistence.entity.LicenseJpaEntity;
import com.hemodialyse.backend.infrastructure.security.license.LicenseKeyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Runs only on client instances (never on the vendor's own authority instance) that
 * have an authority URL configured. Confirms with the authority that each locally
 * active license's {@code jti} has not been remotely revoked, and self-heals if a
 * local database row was tampered with to read ACTIVE when the authority disagrees.
 *
 * <p>Network failures are logged and otherwise ignored here — the offline grace
 * period itself is enforced in {@link LicenseService#verify}, based on how long ago
 * the last successful check was, not by this job blocking anything directly.
 */
@Component
public class LicenseOnlineCheckJob {

    private static final Logger log = LoggerFactory.getLogger(LicenseOnlineCheckJob.class);

    private final LicenseService licenseService;
    private final LicenseKeyProperties keyProperties;
    private final RestClient restClient = RestClient.builder()
            .requestFactory(clientRequestFactory())
            .build();

    public LicenseOnlineCheckJob(LicenseService licenseService, LicenseKeyProperties keyProperties) {
        this.licenseService = licenseService;
        this.keyProperties = keyProperties;
    }

    private static org.springframework.http.client.ClientHttpRequestFactory clientRequestFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        return factory;
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void checkAll() {
        if (keyProperties.isAuthority()) {
            return; // The authority instance IS the source of truth; nothing to check against.
        }
        if (!StringUtils.hasText(keyProperties.authorityUrl())) {
            log.debug("Aucune URL d'autorité de licence configurée — vérification en ligne désactivée.");
            return;
        }

        for (LicenseJpaEntity license : licenseService.listAll()) {
            if (!LicenseService.STATUS_ACTIVE.equals(license.getStatus())) {
                continue;
            }
            checkOne(license);
        }
    }

    private void checkOne(LicenseJpaEntity license) {
        try {
            String url = keyProperties.authorityUrl() + "/api/v1/licenses/authority/verify?jti=" + license.getJti();
            Map<?, ?> response = restClient.get().uri(url).retrieve().body(Map.class);
            boolean revoked = response != null && Boolean.TRUE.equals(response.get("revoked"));
            if (revoked) {
                log.warn("Licence {} révoquée à distance — application locale de la révocation.", license.getJti());
                licenseService.revoke(license.getId(), "Révoquée à distance (vérification en ligne)");
            } else {
                licenseService.recordOnlineCheck(license.getId());
            }
        } catch (Exception e) {
            log.warn("Vérification en ligne de la licence {} impossible ({}) — la période de grâce hors-ligne s'applique.",
                    license.getJti(), e.getMessage());
        }
    }
}
