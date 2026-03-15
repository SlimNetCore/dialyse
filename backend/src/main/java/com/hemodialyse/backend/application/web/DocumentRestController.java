package com.hemodialyse.backend.application.web;

import com.hemodialyse.backend.infrastructure.reporting.JasperReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Contrôleur REST pour la gestion des modèles de documents et l'impression.
 *
 * Workflow impression :
 *   1. Le front envoie : POST /api/v1/documents/print
 *      avec { centerId, typeDocument, params: { patientId, ... } }
 *   2. Le back récupère le modèle de document pour ce centre + type
 *   3. Il lit le chemin .jrxml et le format d'impression
 *   4. Il appelle JasperReportService.generateReport(chemin, params, format)
 *   5. Il retourne le fichier (PDF, Excel ou HTML)
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentRestController {

    private static final Logger log = LoggerFactory.getLogger(DocumentRestController.class);

    private final JdbcTemplate jdbc;
    private final JasperReportService jasperService;

    public DocumentRestController(JdbcTemplate jdbc, JasperReportService jasperService) {
        this.jdbc = jdbc;
        this.jasperService = jasperService;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CRUD Modèles de documents
    // ═══════════════════════════════════════════════════════════════════

    record ModeleDocumentDto(
        UUID id, UUID centerId, String code, String libelle,
        String typeDocument, String cheminJrxml, String formatImpression,
        String description, boolean active
    ) {}

    /** Liste des modèles pour un centre */
    @GetMapping("/modeles")
    public ResponseEntity<?> listModeles(@RequestParam UUID centerId,
                                         @RequestParam(required = false) String typeDocument) {
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

    /** Créer un modèle */
    @PostMapping("/modeles")
    public ResponseEntity<?> createModele(@RequestBody ModeleDocumentDto req) {
        UUID id = req.id() != null ? req.id() : UUID.randomUUID();
        jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, " +
                        "chemin_jrxml, format_impression, description, active) VALUES (?,?,?,?,?,?,?,?,?)",
                id, req.centerId(), req.code(), req.libelle(), req.typeDocument(),
                req.cheminJrxml(), req.formatImpression(), req.description(), req.active());
        return ResponseEntity.ok(Map.of("id", id));
    }

    /** Modifier un modèle */
    @PutMapping("/modeles/{id}")
    public ResponseEntity<?> updateModele(@PathVariable UUID id, @RequestBody ModeleDocumentDto req) {
        jdbc.update("UPDATE modele_document SET code=?, libelle=?, type_document=?, " +
                        "chemin_jrxml=?, format_impression=?, description=?, active=? " +
                        "WHERE id=? AND center_id=?",
                req.code(), req.libelle(), req.typeDocument(),
                req.cheminJrxml(), req.formatImpression(), req.description(), req.active(),
                id, req.centerId());
        return ResponseEntity.ok(Map.of("id", id));
    }

    /** Supprimer un modèle */
    @DeleteMapping("/modeles/{id}")
    public ResponseEntity<?> deleteModele(@PathVariable UUID id, @RequestParam UUID centerId) {
        jdbc.update("DELETE FROM modele_document WHERE id = ? AND center_id = ?", id, centerId);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    /** Types de documents disponibles */
    @GetMapping("/types")
    public ResponseEntity<?> availableTypes() {
        return ResponseEntity.ok(List.of(
                Map.of("code", "FICHE_PATIENT", "label", "Fiche signalétique patient"),
                Map.of("code", "ATTESTATION", "label", "Attestation d'ouverture de droit"),
                Map.of("code", "PEC", "label", "Prise en charge"),
                Map.of("code", "LISTE_PATIENTS", "label", "Liste des patients"),
                Map.of("code", "CUSTOM", "label", "Rapport personnalisé")
        ));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  IMPRESSION
    // ═══════════════════════════════════════════════════════════════════

    record PrintRequest(
        UUID centerId,
        String typeDocument,
        String formatOverride,       // null = utilise le format du modèle
        Map<String, String> params   // patientId, pecId, etc.
    ) {}

    /**
     * Point d'entrée principal pour l'impression.
     * Récupère le modèle → chemin .jrxml → compile → remplit → exporte.
     */
    @PostMapping("/print")
    public ResponseEntity<byte[]> print(@RequestBody PrintRequest req) {
        try {
            // 1) Récupérer le modèle actif pour ce centre et ce type
            Map<String, Object> modele = jdbc.queryForMap(
                    "SELECT chemin_jrxml, format_impression FROM modele_document " +
                            "WHERE center_id = ? AND type_document = ? AND active = TRUE " +
                            "ORDER BY created_at DESC LIMIT 1",
                    req.centerId(), req.typeDocument()
            );

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
            if (req.params() != null) {
                req.params().forEach(jasperParams::put);
            }

            // 3) Générer le rapport
            log.info("Impression: type={}, centre={}, jrxml={}, format={}",
                    req.typeDocument(), req.centerId(), cheminJrxml, format);
            byte[] data = jasperService.generateReport(cheminJrxml, jasperParams, format);

            // 4) Construire la réponse
            return buildResponse(data, format, req.typeDocument());

        } catch (Exception e) {
            log.error("Erreur impression: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Erreur d'impression: " + e.getMessage()).getBytes());
        }
    }

    /**
     * Impression par ID de modèle spécifique (utile si plusieurs modèles du même type).
     */
    @PostMapping("/print/{modeleId}")
    public ResponseEntity<byte[]> printById(@PathVariable UUID modeleId,
                                            @RequestBody PrintRequest req) {
        try {
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
            if (req.params() != null) {
                req.params().forEach(jasperParams::put);
            }

            byte[] data = jasperService.generateReport(cheminJrxml, jasperParams, format);
            return buildResponse(data, format, typeDoc);

        } catch (Exception e) {
            log.error("Erreur impression par ID: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Erreur: " + e.getMessage()).getBytes());
        }
    }

    // ─── Helper ─────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> buildResponse(byte[] data, String format, String typeDoc) {
        String filename = typeDoc.toLowerCase().replace("_", "-");
        HttpHeaders headers = new HttpHeaders();

        return switch (format.toUpperCase()) {
            case "EXCEL", "XLS", "XLSX" -> {
                headers.setContentType(MediaType.parseMediaType(
                        "application/vnd.ms-excel"));
                headers.add("Content-Disposition",
                        "attachment; filename=" + filename + ".xls");
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

