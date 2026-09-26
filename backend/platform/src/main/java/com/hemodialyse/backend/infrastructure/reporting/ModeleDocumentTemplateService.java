package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.application.reporting.JrxmlSecurityValidator;
import com.hemodialyse.backend.application.reporting.JrxmlSecurityValidator.Violation;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.persistence.entity.ModeleDocumentVersionJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.ModeleDocumentVersionJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gestion sécurisée des modèles d'impression personnalisés d'un centre : téléversement validé, historique
 * des versions, activation / retour arrière et retour au modèle d'origine.
 * <p>
 * Toute opération est bornée au centre de l'appelant : un modèle d'un autre centre est traité comme inexistant.
 */
@Service
public class ModeleDocumentTemplateService {

    private static final Logger log = LoggerFactory.getLogger(ModeleDocumentTemplateService.class);
    private static final Pattern SAFE_TEMPLATE_NAME = Pattern.compile("[A-Za-z0-9_\\-]+\\.jrxml");
    private static final int MAX_COMMENT = 500;
    private final JdbcTemplate jdbc;
    private final ModeleDocumentVersionJpaRepository versions;
    private final CustomTemplateCompiler compiler;
    private final JrxmlSecurityValidator validator = new JrxmlSecurityValidator();

    public ModeleDocumentTemplateService(JdbcTemplate jdbc, ModeleDocumentVersionJpaRepository versions,
                                         CustomTemplateCompiler compiler) {
        this.jdbc = jdbc;
        this.versions = versions;
        this.compiler = compiler;
    }

    /**
     * Nom de fichier sûr (sans chemin) d'un chemin de modèle, ou {@code null}.
     */
    static String safeTemplateName(String path) {
        if (path == null) return null;
        String cleaned = path.replace('\\', '/');
        String name = cleaned.substring(cleaned.lastIndexOf('/') + 1);
        return SAFE_TEMPLATE_NAME.matcher(name).matches() ? name : null;
    }

    private static UploadOutcome rejected(String code, String detail) {
        return new UploadOutcome(null, List.of(new Violation(code, detail)));
    }

    private static String truncate(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > MAX_COMMENT ? t.substring(0, MAX_COMMENT) : t;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ───────────────────────────── Lecture ─────────────────────────────

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage();
        return message == null ? root.getClass().getSimpleName() : message.lines().findFirst().orElse(message);
    }

    public Optional<ModeleInfo> findModele(UUID centerId, UUID modeleId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id, center_id, code, type_document, chemin_jrxml FROM modele_document "
                            + "WHERE id = ? AND center_id = ?",
                    (rs, i) -> new ModeleInfo(rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class),
                            rs.getString("code"), rs.getString("type_document"), rs.getString("chemin_jrxml")),
                    modeleId, centerId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Source du modèle d'origine livré avec l'application, s'il en existe un pour ce modèle.
     */
    public Optional<String> builtInSource(ModeleInfo modele) {
        String name = safeTemplateName(modele.cheminJrxml());
        if (name == null) return Optional.empty();
        ClassPathResource resource = new ClassPathResource("reports/" + name);
        if (!resource.exists()) return Optional.empty();
        try (var in = resource.getInputStream()) {
            return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Modèle d'origine illisible : {}", name, e);
            return Optional.empty();
        }
    }

    public Optional<ActiveCustomTemplate> activeCustom(UUID centerId, UUID modeleId) {
        return versions.findByModeleIdAndCenterIdAndActifTrue(modeleId, centerId)
                .map(v -> new ActiveCustomTemplate(v.getVersion(), v.getContenu()));
    }

    public PagedResult<VersionView> listVersions(UUID centerId, UUID modeleId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Page<ModeleDocumentVersionJpaEntity> result = versions.findByModeleIdAndCenterIdOrderByVersionDesc(
                modeleId, centerId, PageRequest.of(safePage, safeSize));
        return PagedResult.of(result.map(VersionView::of).getContent(), result.getTotalElements(), safePage, safeSize);
    }

    // ───────────────────────────── Écriture ─────────────────────────────

    /**
     * Contenu d'une version précise (téléchargement pour édition).
     */
    public Optional<String> versionSource(UUID centerId, UUID modeleId, int version) {
        return versions.findByModeleIdAndCenterIdAndVersion(modeleId, centerId, version)
                .map(ModeleDocumentVersionJpaEntity::getContenu);
    }

    /**
     * Valide puis enregistre un fichier téléversé comme nouvelle version active du modèle.
     * Aucune donnée n'est conservée si une anomalie est détectée.
     */
    @Transactional
    public UploadOutcome upload(ModeleInfo modele, String originalFilename, byte[] bytes, String commentaire,
                                String uploadedBy) {
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".jrxml")) {
            return rejected("FILE_TYPE", "Extension .jrxml attendue.");
        }
        if (bytes == null || bytes.length == 0) {
            return rejected("EMPTY", "Le fichier est vide.");
        }
        if (bytes.length > JrxmlSecurityValidator.MAX_BYTES) {
            return rejected("TOO_LARGE", "Taille maximale : " + JrxmlSecurityValidator.MAX_BYTES / 1024 + " Ko.");
        }
        String reference = builtInSource(modele).orElse(null);
        if (reference == null) {
            return rejected("NOT_CUSTOMIZABLE", "Ce modèle n'a pas de modèle d'origine personnalisable.");
        }
        String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return rejected("ENCODING", "Le fichier doit être encodé en UTF-8.");
        }

        List<Violation> violations = validator.validate(content, reference);
        if (!violations.isEmpty()) {
            log.warn("Modèle refusé (centre={}, modèle={}, par={}) : {}", modele.centerId(), modele.id(),
                    uploadedBy, violations);
            return new UploadOutcome(null, violations);
        }
        try {
            compiler.compileNoCache(content);
        } catch (Exception e) {
            log.warn("Compilation impossible (centre={}, modèle={}) : {}", modele.centerId(), modele.id(), e.getMessage());
            return rejected("COMPILE_FAILED", rootMessage(e));
        }

        versions.findByModeleIdAndCenterIdAndActifTrueOrderByVersionDesc(modele.id(), modele.centerId())
                .forEach(v -> v.setActif(false));
        int next = versions.maxVersion(modele.id(), modele.centerId()) + 1;
        String sha = sha256(bytes);
        ModeleDocumentVersionJpaEntity saved = versions.save(new ModeleDocumentVersionJpaEntity(
                UUID.randomUUID(), modele.id(), modele.centerId(), next, content, sha, bytes.length,
                truncate(commentaire), uploadedBy, OffsetDateTime.now(ZoneOffset.UTC), true));
        log.info("Modèle personnalisé enregistré : centre={}, modèle={}, version={}, sha256={}, par={}",
                modele.centerId(), modele.id(), next, sha, uploadedBy);
        return new UploadOutcome(VersionView.of(saved), List.of());
    }

    /**
     * Réactive une ancienne version (retour arrière).
     */
    @Transactional
    public Optional<VersionView> activate(UUID centerId, UUID modeleId, int version) {
        Optional<ModeleDocumentVersionJpaEntity> target =
                versions.findByModeleIdAndCenterIdAndVersion(modeleId, centerId, version);
        if (target.isEmpty()) return Optional.empty();
        versions.findByModeleIdAndCenterIdAndActifTrueOrderByVersionDesc(modeleId, centerId)
                .forEach(v -> v.setActif(false));
        ModeleDocumentVersionJpaEntity entity = target.get();
        entity.setActif(true);
        log.info("Version de modèle activée : centre={}, modèle={}, version={}", centerId, modeleId, version);
        return Optional.of(VersionView.of(entity));
    }

    /**
     * Revient au modèle d'origine : aucune version personnalisée n'est plus active (l'historique est conservé).
     */
    @Transactional
    public void reset(UUID centerId, UUID modeleId) {
        versions.findByModeleIdAndCenterIdAndActifTrueOrderByVersionDesc(modeleId, centerId)
                .forEach(v -> v.setActif(false));
        log.info("Retour au modèle d'origine : centre={}, modèle={}", centerId, modeleId);
    }

    // ───────────────────────────── Utilitaires ─────────────────────────────

    @Transactional
    public void deleteAllVersions(UUID modeleId) {
        versions.deleteByModeleId(modeleId);
    }

    /**
     * Modèle d'un centre (ligne de {@code modele_document}).
     */
    public record ModeleInfo(UUID id, UUID centerId, String code, String typeDocument, String cheminJrxml) {
    }

    /**
     * Version personnalisée, sans son contenu.
     */
    public record VersionView(UUID id, int version, boolean actif, String sha256, int tailleOctets,
                              String commentaire, String uploadedBy, OffsetDateTime uploadedAt) {
        static VersionView of(ModeleDocumentVersionJpaEntity e) {
            return new VersionView(e.getId(), e.getVersion(), e.isActif(), e.getSha256(), e.getTailleOctets(),
                    e.getCommentaire(), e.getUploadedBy(), e.getUploadedAt());
        }
    }

    /**
     * Version personnalisée active, avec son contenu, destinée à l'impression.
     */
    public record ActiveCustomTemplate(int version, String contenu) {
    }

    /**
     * Résultat d'un téléversement : soit une version créée, soit la liste des anomalies.
     */
    public record UploadOutcome(VersionView version, List<Violation> violations) {
        public boolean accepted() {
            return version != null;
        }
    }
}
