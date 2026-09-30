package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.reporting.CustomTemplateCompiler;
import com.hemodialyse.backend.infrastructure.reporting.DocumentIdentityProvider;
import com.hemodialyse.backend.infrastructure.reporting.JasperReportService;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentCatalog;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentPrinter;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentTemplateService;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.ModeleDocumentDto;
import com.hemodialyse.backend.infrastructure.web.dto.request.DocumentSearchRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.PrintRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Contrôleur REST pour la gestion des modèles de documents et l'impression.
 * <p>
 * Workflow impression :
 * 1. Le front envoie : POST /api/v1/documents/print
 * avec { centerId, typeDocument, params: { patientId, ... } }
 * 2. Le back récupère le modèle de document pour ce centre + type
 * 3. Il lit le chemin .jrxml et le format d'impression
 * 4. Il appelle JasperReportService.generateReport(chemin, params, format)
 * 5. Il retourne le fichier (PDF, Excel ou HTML)
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentRestController {

    private static final Logger log = LoggerFactory.getLogger(DocumentRestController.class);

    private final JdbcTemplate jdbc;
    private final JasperReportService jasperService;
    /**
     * Chemins de modèles autorisés : uniquement les modèles livrés avec l'application (jamais un chemin disque libre).
     */
    private static final java.util.regex.Pattern SAFE_TEMPLATE_PATH =
            java.util.regex.Pattern.compile("reports/[A-Za-z0-9_\\-]+\\.jrxml");
    private final ModeleDocumentTemplateService templateService;
    private final CustomTemplateCompiler customTemplateCompiler;
    private final CenterAccessGuard centerAccessGuard;
    private final DocumentIdentityProvider identityProvider;
    private final ModeleDocumentPrinter printer;

    public DocumentRestController(JdbcTemplate jdbc, JasperReportService jasperService,
                                  ModeleDocumentTemplateService templateService,
                                  CustomTemplateCompiler customTemplateCompiler,
                                  CenterAccessGuard centerAccessGuard,
                                  DocumentIdentityProvider identityProvider,
                                  ModeleDocumentPrinter printer) {
        this.jdbc = jdbc;
        this.jasperService = jasperService;
        this.templateService = templateService;
        this.customTemplateCompiler = customTemplateCompiler;
        this.centerAccessGuard = centerAccessGuard;
        this.identityProvider = identityProvider;
        this.printer = printer;
    }

    private static void requireSafeTemplatePath(String path) {
        if (path == null || !SAFE_TEMPLATE_PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Chemin de modèle invalide : attendu reports/<nom>.jrxml");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CRUD Modèles de documents
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Liste des modèles pour un centre
     */
    @GetMapping("/modeles")
    public ResponseEntity<?> listModeles(@RequestParam UUID centerId,
                                         @RequestParam(required = false) String typeDocument) {
        centerId = centerAccessGuard.requireCenter(centerId).value();
        String sql = "SELECT id, center_id, code, libelle, type_document, chemin_jrxml, " +
                "format_impression, description, active FROM modele_document " +
                "WHERE center_id = ? " +
                (typeDocument != null ? "AND type_document = ? " : "") +
                "ORDER BY libelle";

        var rows = typeDocument != null
                ? jdbc.queryForList(sql, centerId, typeDocument)
                : jdbc.queryForList(sql, centerId);
        return ResponseEntity.ok(rows);
    }

    /**
     * Recherche des modèles avec critères structurés
     */
    @PostMapping("/modeles/search")
    public ResponseEntity<?> searchModeles(@RequestBody @Valid DocumentSearchRequest criteria) {
        centerAccessGuard.requireCenter(criteria.centerId());
        String sql = "SELECT id, center_id, code, libelle, type_document, chemin_jrxml, " +
                "format_impression, description, active FROM modele_document " +
                "WHERE center_id = ? " +
                (criteria.typeDocument() != null ? "AND type_document = ? " : "") +
                "ORDER BY libelle " +
                "LIMIT ? OFFSET ?";

        java.util.List<?> rows;
        if (criteria.typeDocument() != null) {
            rows = jdbc.queryForList(sql, criteria.centerId(), criteria.typeDocument(),
                    criteria.size(), criteria.page() * criteria.size());
        } else {
            rows = jdbc.queryForList(sql, criteria.centerId(),
                    criteria.size(), criteria.page() * criteria.size());
        }
        return ResponseEntity.ok(rows);
    }

    /**
     * Créer un modèle
     */
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    @PostMapping("/modeles")
    public ResponseEntity<?> createModele(@RequestBody ModeleDocumentDto req) {
        UUID centerId = centerAccessGuard.requireCenter(req.centerId()).value();
        requireSafeTemplatePath(req.cheminJrxml());
        UUID id = req.id() != null ? req.id() : UUID.randomUUID();
        jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, " +
                        "chemin_jrxml, format_impression, description, active) VALUES (?,?,?,?,?,?,?,?,?)",
                id, centerId, req.code(), req.libelle(), req.typeDocument(),
                req.cheminJrxml(), req.formatImpression(), req.description(), req.active());
        return ResponseEntity.ok(Map.of("id", id));
    }

    /**
     * Modifier un modèle
     */
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    @PutMapping("/modeles/{id}")
    public ResponseEntity<?> updateModele(@PathVariable UUID id, @RequestBody ModeleDocumentDto req) {
        UUID centerId = centerAccessGuard.requireCenter(req.centerId()).value();
        requireSafeTemplatePath(req.cheminJrxml());
        jdbc.update("UPDATE modele_document SET code=?, libelle=?, type_document=?, " +
                        "chemin_jrxml=?, format_impression=?, description=?, active=? " +
                        "WHERE id=? AND center_id=?",
                req.code(), req.libelle(), req.typeDocument(),
                req.cheminJrxml(), req.formatImpression(), req.description(), req.active(),
                id, centerId);
        return ResponseEntity.ok(Map.of("id", id));
    }

    /**
     * Supprimer un modèle
     */
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    @DeleteMapping("/modeles/{id}")
    public ResponseEntity<?> deleteModele(@PathVariable UUID id, @RequestParam UUID centerId) {
        centerId = centerAccessGuard.requireCenter(centerId).value();
        if (templateService.findModele(centerId, id).isPresent()) {
            templateService.deleteAllVersions(id);
        }
        jdbc.update("DELETE FROM modele_document WHERE id = ? AND center_id = ?", id, centerId);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    /**
     * Types de documents disponibles : le catalogue des documents livrés, plus les rapports personnalisés.
     */
    @GetMapping("/types")
    public ResponseEntity<?> availableTypes() {
        List<Map<String, String>> types = new ArrayList<>();
        ModeleDocumentCatalog.entries().forEach(e -> types.add(Map.of("code", e.type(), "label", e.libelle())));
        types.add(Map.of("code", "CUSTOM", "label", "Rapport personnalisé"));
        return ResponseEntity.ok(types);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  IMPRESSION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Point d'entrée principal pour l'impression.
     * Récupère le modèle → chemin .jrxml → compile → remplit → exporte.
     */
    @PostMapping("/print")
    public ResponseEntity<byte[]> print(@RequestBody PrintRequest req) {
        try {
            validatePrintRequest(req, true);
            String normalizedType = req.typeDocument().trim().toUpperCase(Locale.ROOT);
            // 1) Récupérer le modèle actif pour ce centre et ce type
            var modeles = jdbc.queryForList(
                    "SELECT id, chemin_jrxml, format_impression FROM modele_document " +
                            "WHERE center_id = ? AND UPPER(type_document) = ? AND active = TRUE " +
                            "ORDER BY created_at DESC",
                    req.centerId(), normalizedType
            );
            if (modeles.isEmpty()) {
                String msg = "Aucun modèle actif trouvé pour ce centre/type: " + normalizedType + " (centre=" + req.centerId() + ")";
                log.warn(msg);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(msg.getBytes());
            }
            Map<String, Object> modele = modeles.get(0);

            String cheminJrxml = Objects.toString(
                    modele.get("CHEMIN_JRXML"),
                    Objects.toString(modele.get("chemin_jrxml"), ""));
            String format = req.formatOverride() != null
                    ? req.formatOverride()
                    : Objects.toString(modele.get("FORMAT_IMPRESSION"),
                    Objects.toString(modele.get("format_impression"), "PDF"));

            // 2) Construire les paramètres Jasper
            Map<String, Object> jasperParams = new HashMap<>();
            jasperParams.put("CENTER_ID", req.centerId().toString());
            DocumentIdentityProvider.stripReservedParams(normalizeParams(req.params())).forEach(jasperParams::put);
            // Identité société + centre (en-tête et pied de page) : posée par le serveur, jamais par le client.
            jasperParams.putAll(identityProvider.paramsFor(req.centerId()));

            // 3) Générer le rapport
            log.info("Impression: type={}, centre={}, jrxml={}, format={}",
                    normalizedType, req.centerId(), cheminJrxml, format);
            byte[] data = render(UUID.fromString(Objects.toString(
                            modele.getOrDefault("ID", modele.get("id")))),
                    req.centerId(), cheminJrxml, jasperParams, format);

            // 4) Construire la réponse
            return buildResponse(data, format, normalizedType);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(e.getMessage().getBytes());
        } catch (Exception e) {
            log.error("Erreur impression: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Erreur d'impression: " + e.getMessage()).getBytes());
        } catch (Throwable t) {
            // Capture errors like NoClassDefFoundError to avoid security fallback 403 on /error dispatch
            log.error("Erreur critique impression: {}", t.getMessage(), t);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Erreur critique d'impression: " + t.getClass().getSimpleName() + " - " + t.getMessage()).getBytes());
        }
    }

    /**
     * Impression par ID de modèle spécifique (utile si plusieurs modèles du même type).
     */
    @PostMapping("/print/{modeleId}")
    public ResponseEntity<byte[]> printById(@PathVariable UUID modeleId,
                                            @RequestBody PrintRequest req) {
        try {
            validatePrintRequest(req, false);
            Map<String, Object> modele = jdbc.queryForMap(
                    "SELECT chemin_jrxml, format_impression, type_document FROM modele_document " +
                            "WHERE id = ? AND center_id = ?",
                    modeleId, req.centerId()
            );

            String cheminJrxml = Objects.toString(
                    modele.get("CHEMIN_JRXML"),
                    Objects.toString(modele.get("chemin_jrxml"), ""));
            String typeDoc = Objects.toString(
                    modele.get("TYPE_DOCUMENT"),
                    Objects.toString(modele.get("type_document"), "CUSTOM"));
            String format = req.formatOverride() != null
                    ? req.formatOverride()
                    : Objects.toString(modele.get("FORMAT_IMPRESSION"),
                    Objects.toString(modele.get("format_impression"), "PDF"));

            Map<String, Object> jasperParams = new HashMap<>();
            jasperParams.put("CENTER_ID", req.centerId().toString());
            DocumentIdentityProvider.stripReservedParams(normalizeParams(req.params())).forEach(jasperParams::put);
            // Identité société + centre (en-tête et pied de page) : posée par le serveur, jamais par le client.
            jasperParams.putAll(identityProvider.paramsFor(req.centerId()));

            byte[] data = render(modeleId, req.centerId(), cheminJrxml, jasperParams, format);
            return buildResponse(data, format, typeDoc);

        } catch (EmptyResultDataAccessException e) {
            String msg = "Modèle introuvable pour ce centre";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(msg.getBytes());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(e.getMessage().getBytes());
        } catch (Exception e) {
            log.error("Erreur impression par ID: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Erreur: " + e.getMessage()).getBytes());
        } catch (Throwable t) {
            log.error("Erreur critique impression par ID: {}", t.getMessage(), t);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Erreur critique: " + t.getClass().getSimpleName() + " - " + t.getMessage()).getBytes());
        }
    }

    /**
     * Génère le document : utilise la version personnalisée active du modèle si le centre en a téléversé une,
     * sinon le modèle d'origine.
     */
    private byte[] render(UUID modeleId, UUID centerId, String cheminJrxml, Map<String, Object> params, String format)
            throws Exception {
        return printer.render(modeleId, centerId, cheminJrxml, params, format);
    }

    private void validatePrintRequest(PrintRequest req, boolean requireTypeDocument) {
        if (req == null || req.centerId() == null) {
            throw new IllegalArgumentException("centerId est obligatoire");
        }
        if (requireTypeDocument && (req.typeDocument() == null || req.typeDocument().isBlank())) {
            throw new IllegalArgumentException("typeDocument est obligatoire");
        }
        if (!requireTypeDocument) return;

        String normalizedType = req.typeDocument().trim().toUpperCase(Locale.ROOT);
        Map<String, String> params = normalizeParams(req.params());
        String patientId = params.get("patientId");
        String attestationId = params.get("attestationId");
        String pecId = params.get("pecId");
        String ordonnanceId = params.get("ordonnanceId");

        if ("FICHE_PATIENT".equals(normalizedType)) {
            if (isBlank(patientId)) {
                throw new IllegalArgumentException("patientId est obligatoire pour " + normalizedType);
            }
            return;
        }

        if ("ATTESTATION".equals(normalizedType) && isBlank(patientId) && isBlank(attestationId)) {
            throw new IllegalArgumentException("patientId ou attestationId est obligatoire pour ATTESTATION");
        }

        if ("PEC".equals(normalizedType) && isBlank(patientId) && isBlank(pecId)) {
            throw new IllegalArgumentException("patientId ou pecId est obligatoire pour PEC");
        }

        if ("ORDONNANCE".equals(normalizedType) && isBlank(ordonnanceId)) {
            throw new IllegalArgumentException("ordonnanceId est obligatoire pour ORDONNANCE");
        }

        if ("SYNTHESE_FACTURATION_MENSUELLE".equals(normalizedType)) {
            String periodStart = params.get("PERIOD_START");
            String periodEnd = params.get("PERIOD_END");
            if (isBlank(periodStart) || isBlank(periodEnd)) {
                throw new IllegalArgumentException("PERIOD_START et PERIOD_END sont obligatoires pour SYNTHESE_FACTURATION_MENSUELLE");
            }
        }
    }

    private Map<String, String> normalizeParams(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        Map<String, String> normalized = new HashMap<>();
        raw.forEach((k, v) -> normalized.put(k, v == null ? null : v.trim()));
        return normalized;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ─── Helper ─────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> buildResponse(byte[] data, String format, String typeDoc) {
        String filename = typeDoc.toLowerCase().replace("_", "-");
        HttpHeaders headers = new HttpHeaders();

        return switch (format.toUpperCase()) {
            case "EXCEL", "XLS", "XLSX" -> {
                headers.setContentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                headers.add("Content-Disposition",
                        "attachment; filename=" + filename + ".xlsx");
                yield ResponseEntity.ok().headers(headers).body(data);
            }
            case "HTML" -> {
                headers.setContentType(MediaType.TEXT_HTML);
                yield ResponseEntity.ok().headers(headers).body(data);
            }
            default -> {
                headers.setContentType(MediaType.APPLICATION_PDF);
                headers.add("Content-Disposition",
                        "inline; filename=" + filename + ".pdf");
                yield ResponseEntity.ok().headers(headers).body(data);
            }
        };
    }
}




