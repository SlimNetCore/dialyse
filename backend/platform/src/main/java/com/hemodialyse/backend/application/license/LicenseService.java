package com.hemodialyse.backend.application.license;

import com.hemodialyse.backend.infrastructure.persistence.entity.CenterJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.LicenseJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.CenterJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.LicenseJpaRepository;
import com.hemodialyse.backend.infrastructure.security.license.LicenseKeyProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates license issuance, activation, revocation and verification.
 *
 * <p>Security property: expiry, seat count and center binding are read back from the
 * cryptographically signed token itself ({@link LicenseTokenService#verify}), never
 * from the plain database columns that merely cache them for display — editing those
 * columns directly in the database has no effect on enforcement. Only REVOCATION is
 * necessarily a database/authority-side fact (a signed token cannot revoke itself);
 * its integrity relies on the periodic online check reconciling against the
 * authority's own record for the same {@code jti} (see {@code LicenseOnlineCheckJob}).
 */
@Service
public class LicenseService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";

    private final LicenseJpaRepository licenseRepository;
    private final CenterJpaRepository centerRepository;
    private final LicenseTokenService tokenService;
    private final LicenseKeyProperties keyProperties;
    private final JdbcTemplate jdbc;

    public LicenseService(LicenseJpaRepository licenseRepository,
                          CenterJpaRepository centerRepository,
                          LicenseTokenService tokenService,
                          LicenseKeyProperties keyProperties,
                          JdbcTemplate jdbc) {
        this.licenseRepository = licenseRepository;
        this.centerRepository = centerRepository;
        this.keyProperties = keyProperties;
        this.tokenService = tokenService;
        this.jdbc = jdbc;
    }

    @Transactional
    public LicenseJpaEntity issue(UUID centerId, String type, int maxUsers, Instant validFrom, Instant validUntil,
                                  UUID createdBy) {
        CenterJpaEntity center = centerRepository.findById(centerId)
                .orElseThrow(() -> new IllegalArgumentException("Centre introuvable : " + centerId));

        LicenseTokenService.IssuedToken issued = tokenService.issue(
                centerId, center.getCode(), type, maxUsers, validFrom, validUntil);

        LicenseJpaEntity entity = new LicenseJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setCenterId(centerId);
        entity.setLicenseKey(issued.compact());
        entity.setJti(issued.jti());
        entity.setType(type);
        entity.setMaxUsers(maxUsers);
        entity.setValidFrom(toOffset(validFrom));
        entity.setValidUntil(toOffset(validUntil));
        entity.setStatus(STATUS_ACTIVE);
        entity.setActivatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return licenseRepository.save(entity);
    }

    /**
     * Activates a license key generated elsewhere (offline activation flow) for a given center.
     */
    @Transactional
    public LicenseJpaEntity activate(UUID centerId, String rawLicenseKey, UUID activatedBy) {
        LicenseTokenService.LicenseClaims claims = tokenService.verify(rawLicenseKey);
        if (!claims.centerId().equals(centerId)) {
            throw new InvalidLicenseException("Cette licence a été émise pour un autre centre.");
        }
        if (licenseRepository.findByJti(claims.jti()).isPresent()) {
            throw new InvalidLicenseException("Cette licence a déjà été activée.");
        }

        LicenseJpaEntity entity = new LicenseJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setCenterId(centerId);
        entity.setLicenseKey(rawLicenseKey);
        entity.setJti(claims.jti());
        entity.setType(claims.type());
        entity.setMaxUsers(claims.maxUsers());
        entity.setValidFrom(toOffset(claims.notBefore() != null ? claims.notBefore() : Instant.now()));
        entity.setValidUntil(toOffset(claims.expiresAt()));
        entity.setStatus(STATUS_ACTIVE);
        entity.setActivatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setCreatedBy(activatedBy);
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return licenseRepository.save(entity);
    }

    @Transactional
    public void revoke(UUID licenseId, String reason) {
        LicenseJpaEntity entity = licenseRepository.findById(licenseId)
                .orElseThrow(() -> new IllegalArgumentException("Licence introuvable : " + licenseId));
        entity.setStatus(STATUS_REVOKED);
        entity.setRevokedReason(reason);
        licenseRepository.save(entity);
    }

    @Transactional
    public void recordOnlineCheck(UUID licenseId) {
        licenseRepository.findById(licenseId).ifPresent(entity -> {
            entity.setLastOnlineCheckAt(OffsetDateTime.now(ZoneOffset.UTC));
            licenseRepository.save(entity);
        });
    }

    public List<LicenseJpaEntity> listAll() {
        return licenseRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<LicenseJpaEntity> listForCenter(UUID centerId) {
        return licenseRepository.findByCenterIdOrderByCreatedAtDesc(centerId);
    }

    /**
     * Re-derives the real, tamper-proof verdict for a center's most recent license
     * record. Never trusts the database's cached expiry/type/seat columns — only the
     * signed token content and the (necessarily database-resident) revoked flag.
     */
    public LicenseVerdict verify(UUID centerId) {
        Optional<LicenseJpaEntity> latest = licenseRepository.findByCenterIdOrderByCreatedAtDesc(centerId)
                .stream().findFirst();
        if (latest.isEmpty()) {
            return LicenseVerdict.none();
        }
        LicenseJpaEntity entity = latest.get();
        if (STATUS_REVOKED.equals(entity.getStatus())) {
            return LicenseVerdict.revoked(entity);
        }
        try {
            LicenseTokenService.LicenseClaims claims = tokenService.verify(entity.getLicenseKey());
            if (!claims.centerId().equals(centerId)) {
                // The stored key does not actually belong to this center's row — treat as tampering.
                return LicenseVerdict.invalid(entity, "La licence enregistrée ne correspond pas à ce centre.");
            }
            if (isOnlineCheckStale(entity)) {
                return LicenseVerdict.invalid(entity,
                        "Vérification en ligne de la licence trop ancienne (> " +
                                keyProperties.offlineGraceDays() + " jours) — vérifiez la connexion Internet du centre.");
            }
            return LicenseVerdict.valid(entity, claims);
        } catch (InvalidLicenseException e) {
            return LicenseVerdict.invalid(entity, e.getMessage());
        }
    }

    /**
     * Only enforced when an authority URL is configured (periodic online check
     * expected) and the instance is not itself the authority. A center that has never
     * completed a check yet gets a grace period counted from activation, not an
     * immediate block.
     */
    private boolean isOnlineCheckStale(LicenseJpaEntity entity) {
        if (!StringUtils.hasText(keyProperties.authorityUrl()) || keyProperties.isAuthority()) {
            return false;
        }
        OffsetDateTime reference = entity.getLastOnlineCheckAt() != null
                ? entity.getLastOnlineCheckAt()
                : entity.getActivatedAt();
        if (reference == null) {
            return false;
        }
        return reference.plusDays(keyProperties.offlineGraceDays()).isBefore(OffsetDateTime.now(ZoneOffset.UTC));
    }

    public int countActiveUsers(UUID centerId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM app_user_center uc INNER JOIN app_user u ON u.id = uc.user_id " +
                        "WHERE uc.center_id = ? AND u.active = TRUE",
                Integer.class, centerId
        );
        return count == null ? 0 : count;
    }

    /**
     * Throws if creating one more active user in this center would exceed its license's seat count.
     */
    public void assertSeatAvailable(UUID centerId) {
        LicenseVerdict verdict = verify(centerId);
        if (!verdict.valid()) {
            throw new LicenseRequiredException("Aucune licence valide pour ce centre.");
        }
        int currentSeats = countActiveUsers(centerId);
        if (currentSeats >= verdict.claims().maxUsers()) {
            throw new LicenseRequiredException(
                    "Quota de postes atteint pour ce centre (" + verdict.claims().maxUsers() +
                            " utilisateur(s) autorisé(s) par la licence).");
        }
    }

    private OffsetDateTime toOffset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    public record LicenseVerdict(boolean valid, String reason, LicenseJpaEntity entity,
                                 LicenseTokenService.LicenseClaims claims) {
        static LicenseVerdict none() {
            return new LicenseVerdict(false, "Aucune licence enregistrée pour ce centre.", null, null);
        }

        static LicenseVerdict revoked(LicenseJpaEntity entity) {
            return new LicenseVerdict(false, "Licence révoquée" +
                    (entity.getRevokedReason() != null ? " : " + entity.getRevokedReason() : "."), entity, null);
        }

        static LicenseVerdict invalid(LicenseJpaEntity entity, String reason) {
            return new LicenseVerdict(false, reason, entity, null);
        }

        static LicenseVerdict valid(LicenseJpaEntity entity, LicenseTokenService.LicenseClaims claims) {
            return new LicenseVerdict(true, null, entity, claims);
        }
    }
}
