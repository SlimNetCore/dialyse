package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.application.reporting.LogoImageValidator;
import com.hemodialyse.backend.infrastructure.persistence.entity.SocieteJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.SocieteJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Logo d'une société : envoi validé (image PNG/JPEG contrôlée sur son contenu), lecture, suppression.
 * Le logo est stocké en base, avec la société, et imprimé dans l'en-tête des documents.
 */
@Service
public class SocieteLogoService {

    private static final Logger log = LoggerFactory.getLogger(SocieteLogoService.class);
    private final SocieteJpaRepository societes;
    private final LogoImageValidator validator = new LogoImageValidator();

    public SocieteLogoService(SocieteJpaRepository societes) {
        this.societes = societes;
    }

    @Transactional
    public Optional<UploadOutcome> save(UUID societeId, byte[] bytes, String uploadedBy) {
        Optional<SocieteJpaEntity> entity = societes.findById(societeId);
        if (entity.isEmpty()) return Optional.empty();
        LogoImageValidator.Result result = validator.validate(bytes);
        if (!result.accepted()) {
            log.warn("Logo refusé (société={}, par={}) : {}", societeId, uploadedBy, result.errorCode());
            return Optional.of(new UploadOutcome(false, result.errorCode(), result.detail()));
        }
        entity.get().setLogo(bytes);
        entity.get().setLogoContentType(result.contentType());
        log.info("Logo enregistré (société={}, {} octets, {}, par={})", societeId, bytes.length,
                result.contentType(), uploadedBy);
        return Optional.of(new UploadOutcome(true, null, null));
    }

    @Transactional(readOnly = true)
    public Optional<Logo> find(UUID societeId) {
        return societes.findById(societeId)
                .filter(s -> s.getLogo() != null)
                .map(s -> new Logo(s.getLogo(), s.getLogoContentType()));
    }

    @Transactional
    public boolean delete(UUID societeId) {
        Optional<SocieteJpaEntity> entity = societes.findById(societeId);
        if (entity.isEmpty()) return false;
        entity.get().setLogo(null);
        entity.get().setLogoContentType(null);
        return true;
    }

    /**
     * Sociétés (parmi {@code ids}) qui possèdent un logo — pour l'affichage des listes.
     */
    @Transactional(readOnly = true)
    public Set<UUID> idsWithLogo(Collection<UUID> ids) {
        if (ids.isEmpty()) return Set.of();
        List<UUID> found = societes.findIdsWithLogo(ids);
        return found.stream().collect(Collectors.toSet());
    }

    public record Logo(byte[] content, String contentType) {
    }

    /**
     * Résultat d'un envoi : soit accepté, soit un code d'anomalie (traduit côté interface).
     */
    public record UploadOutcome(boolean accepted, String errorCode, String detail) {
    }
}
