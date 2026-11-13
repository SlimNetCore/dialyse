package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.ReglesDImpressionDesTypesSensibles;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ProfilDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseDeFacturation;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.ModeleDocumentCatalog;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentTemplateService;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.ModeleDocumentDto;
import com.hemodialyse.backend.infrastructure.web.dto.request.DocumentSearchRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Contrôleur REST pour la gestion des modèles de documents (reste en plateforme jusqu'à DOC3 / DOC4).
 * <p>
 * Les routes d'impression {@code POST /print} et {@code POST /print/{modeleId}} sont servies par le contexte Documents
 * (DOC1, ADR 0142). Cette classe fournit encore à Documents, de façon TRANSITOIRE, les règles des types sensibles
 * ({@link ReglesDImpressionDesTypesSensibles}) : profil exigé pour l'ordonnance et la synthèse de facturation, totaux de la synthèse.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentRestController implements ReglesDImpressionDesTypesSensibles {

    private final JdbcTemplate jdbc;
    /**
     * Chemins de modèles autorisés : uniquement les modèles livrés avec l'application (jamais un chemin disque libre).
     */
    private static final java.util.regex.Pattern SAFE_TEMPLATE_PATH =
            java.util.regex.Pattern.compile("reports/[A-Za-z0-9_\\-]+\\.jrxml");
    private final ModeleDocumentTemplateService templateService;
    private final CenterAccessGuard centerAccessGuard;
    private final SyntheseDeFacturation syntheseDeFacturation;

    public DocumentRestController(JdbcTemplate jdbc, ModeleDocumentTemplateService templateService,
                                  CenterAccessGuard centerAccessGuard, SyntheseDeFacturation syntheseDeFacturation) {
        this.jdbc = jdbc;
        this.templateService = templateService;
        this.centerAccessGuard = centerAccessGuard;
        this.syntheseDeFacturation = syntheseDeFacturation;
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
        centerId = centerAccessGuard.requireCenter(centerId).valeur();
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
        UUID centerId = centerAccessGuard.requireCenter(req.centerId()).valeur();
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
        UUID centerId = centerAccessGuard.requireCenter(req.centerId()).valeur();
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
        centerId = centerAccessGuard.requireCenter(centerId).valeur();
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
    //  Règles des types sensibles, fournies au contexte Documents (transitoire)
    // ═══════════════════════════════════════════════════════════════════

    private static final String SYNTHESE_FACTURATION = "SYNTHESE_FACTURATION_MENSUELLE";
    private static final String ORDONNANCE = "ORDONNANCE";

    private static String normaliser(String type) {
        return type == null ? null : type.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public void garderParType(String type, UUID centreDemande) {
        String normalise = normaliser(type);
        if (SYNTHESE_FACTURATION.equals(normalise)) {
            garderLaSyntheseDeFacturation(centreDemande);
        }
        if (ORDONNANCE.equals(normalise)) {
            garderLOrdonnance(centreDemande);
        }
    }

    @Override
    public void garderParModele(String typeDuModele, UUID centreDemande) {
        // Le type du modèle chargé (jamais un paramètre du client) décide de la garde de la synthèse de facturation.
        if (SYNTHESE_FACTURATION.equals(normaliser(typeDuModele))) {
            garderLaSyntheseDeFacturation(centreDemande);
        }
    }

    /**
     * L'impression d'une ordonnance (nom du patient, n° d'assurance, prescripteur) exige les droits de LECTURE des ordonnances
     * (MEDECIN ou ADMIN) et le contrôle du centre du corps : 403 sinon, avant tout accès aux données (KB-MED-18 corrigé, ADR 0081,
     * à confirmer avec les métiers). Les autres types de document ne changent pas.
     */
    private void garderLOrdonnance(UUID centreDemande) {
        var roles = centerAccessGuard.currentScope().roles();
        if (!roles.contains("ROLE_MEDECIN") && !roles.contains("ROLE_ADMIN")) {
            throw new AccessDeniedException("Profil insuffisant pour imprimer une ordonnance");
        }
        centerAccessGuard.requireCenter(centreDemande);
    }

    /**
     * La synthèse de facturation par modèle de document exige le même profil et le même contrôle de centre que
     * {@code POST /facturation/synthese/print} (administrateur ou secrétaire ; le médecin seul est refusé) : 403 sinon
     * (KB-FAC-52 et KB-FAC-60 corrigés). Les autres types de document ne changent pas.
     */
    private void garderLaSyntheseDeFacturation(UUID centreDemande) {
        if (!ProfilDeLaSynthese.autorise(centerAccessGuard.currentScope().roles())) {
            throw new AccessDeniedException("Profil insuffisant pour la synthèse de facturation");
        }
        centerAccessGuard.requireCenter(centreDemande);
    }

    /**
     * Les montants globaux sont les totaux enregistrés des factures de la période (ADR 0034), posés par le serveur : un
     * paramètre du même nom envoyé par le client est écarté (paramètres réservés de Documents, KB-DOC1-2). Période illisible ou
     * inversée : rien n'est posé et le gabarit retombe sur la somme des lignes, comme avant.
     */
    @Override
    public Map<String, Object> donneesDuServeur(String type, UUID centre, Map<String, String> params) {
        if (!SYNTHESE_FACTURATION.equals(normaliser(type))) {
            return Map.of();
        }
        try {
            java.time.LocalDate debut = java.time.LocalDate.parse(params.get("PERIOD_START"));
            java.time.LocalDate fin = java.time.LocalDate.parse(params.get("PERIOD_END"));
            if (debut.isAfter(fin)) {
                return Map.of();
            }
            SyntheseMensuelle synthese = syntheseDeFacturation.de(centre, debut, fin);
            // PL8 : totaux ET lignes du tableau, répartitions, indicateurs : le gabarit ne lit plus aucune table (ADR 0140 D3)
            return new HashMap<>(com.hemodialyse.backend.contexts.facturation.publishedlanguage.ModeleDeLaSynthese.parametresClasses(synthese));
        } catch (NullPointerException e) {
            // période absente : refusée par la suite comme avant
            return Map.of();
        } catch (java.time.DateTimeException e) {
            // période illisible : le gabarit n'a plus de requête qui échoue ; l'échec d'impression (500) d'avant est reproduit (PL8)
            throw new IllegalStateException("Période illisible pour SYNTHESE_FACTURATION_MENSUELLE", e);
        }
    }
}
