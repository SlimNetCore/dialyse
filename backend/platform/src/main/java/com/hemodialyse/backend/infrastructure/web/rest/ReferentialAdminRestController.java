package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.referential.ReferentialAdminApplicationService;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ImportTable;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialCatalogue;
import com.hemodialyse.backend.application.erreur.BusinessException;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.infrastructure.importer.ReferentialTemplateWriter;
import com.hemodialyse.backend.infrastructure.importer.TabularFileReader;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.referential.ImportReportResponse;
import com.hemodialyse.backend.infrastructure.web.dto.referential.ReferentialEntryResponse;
import com.hemodialyse.backend.infrastructure.web.dto.referential.ReferentialKindResponse;
import com.hemodialyse.backend.infrastructure.web.dto.referential.UpsertReferentialEntryRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
import com.hemodialyse.backend.infrastructure.security.AuditRequestFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Façade fine d'administration des référentiels du centre (PL11-1, ADR 0157) : elle distribue chaque {@code {kind}} au
 * fournisseur qui le publie (Patient et couverture : forfaits, caisses, agences, centres payeurs, transporteurs ; en direct
 * pour l'instant : créneaux, salles, médecins traitants). Saisie, import CSV/Excel vérifié, modèles d'import. JSON seul
 * (415/406 sinon). Les générateurs sont des équipements GMAO ({@code /gmao/equipements}).
 * <p>
 * Réservé à l'ADMIN, sur son centre, jamais un autre. Le SUPERADMIN est refusé (403 {@code ROLE_SCOPE}, par le
 * {@code RoleScopeFilter}) : décision KB-PL11-13, reproduite, à confirmer par les métiers avant le 2026-12-15.
 */
@RestController
@RequestMapping("/api/v1/admin/referentials")
@PreAuthorize("hasRole('ADMIN')")
public class ReferentialAdminRestController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReferentialAdminApplicationService service;
    private final ReferentialCatalogue catalogue;
    private final CenterAccessGuard centerAccessGuard;
    private final TabularFileReader fileReader;
    private final ReferentialTemplateWriter templateWriter;

    public ReferentialAdminRestController(ReferentialAdminApplicationService service,
                                          ReferentialCatalogue catalogue,
                                          CenterAccessGuard centerAccessGuard,
                                          TabularFileReader fileReader,
                                          ReferentialTemplateWriter templateWriter) {
        this.service = service;
        this.catalogue = catalogue;
        this.centerAccessGuard = centerAccessGuard;
        this.fileReader = fileReader;
        this.templateWriter = templateWriter;
    }

    /**
     * Référentiels administrables, dans l'ordre d'import conseillé.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ReferentialKindResponse> kinds() {
        return catalogue.kinds().stream()
                .map(ReferentialKindResponse::from)
                .toList();
    }

    @GetMapping(value = "/{kind}", produces = MediaType.APPLICATION_JSON_VALUE)
    public PagedResponse<ReferentialEntryResponse> list(@PathVariable String kind,
                                                        @RequestParam(required = false) UUID centerId,
                                                        @RequestParam(required = false) String search,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        CentreId center =centerAccessGuard.requireCenter(centerId);
        return PagedResponse.from(service.list(center, catalogue.fromSlug(kind), search, page, size),
                ReferentialEntryResponse::from);
    }

    // Pas de `consumes` ici (PL11-2) : il était évalué avant l'autorisation (415 au lieu de 403 pour un rôle non autorisé). Le type
    // de contenu est refusé (415) par la résolution du corps, après AutorisationAvantLeCorpsDesReferentiels.
    @PostMapping(value = "/{kind}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReferentialEntryResponse> create(@PathVariable String kind,
                                                           @RequestBody @Valid UpsertReferentialEntryRequest request,
                                                           HttpServletRequest http) {
        CentreId center =centerAccessGuard.requireCenter(request.centerId());
        ReferentialKind referential = catalogue.fromSlug(kind);
        tracer(http, referential, "création");
        var created = service.create(center, referential, request.values());
        // Audit enrichi (PL11-1, KB-PL11-12) : l'identifiant de l'élément créé (le chemin n'en porte pas).
        http.setAttribute(AuditRequestFilter.ATTRIBUT_IDENTIFIANT, created.id().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReferentialEntryResponse.from(created));
    }

    /** Audit enrichi (PL11-1) : le type de référentiel écrit figure dans le libellé de l'évènement. */
    private static void tracer(HttpServletRequest http, ReferentialKind referential, String action) {
        http.setAttribute(AuditRequestFilter.ATTRIBUT_LIBELLE,
                "Administration des référentiels : " + action + " (" + referential.slug() + ")");
    }

    @PutMapping(value = "/{kind}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReferentialEntryResponse update(@PathVariable String kind, @PathVariable UUID id,
                                           @RequestBody @Valid UpsertReferentialEntryRequest request,
                                           HttpServletRequest http) {
        CentreId center =centerAccessGuard.requireCenter(request.centerId());
        ReferentialKind referential = catalogue.fromSlug(kind);
        tracer(http, referential, "modification");
        return ReferentialEntryResponse.from(service.update(center, referential, id, request.values()));
    }

    @DeleteMapping("/{kind}/{id}")
    public ResponseEntity<Void> delete(@PathVariable String kind, @PathVariable UUID id,
                                       @RequestParam(required = false) UUID centerId, HttpServletRequest http) {
        CentreId center =centerAccessGuard.requireCenter(centerId);
        ReferentialKind referential = catalogue.fromSlug(kind);
        tracer(http, referential, "suppression");
        service.delete(center, referential, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Vérifie ({@code dryRun=true}, par défaut) ou importe un fichier CSV / Excel. Le compte rendu liste les
     * colonnes manquantes, les colonnes ignorées et chaque anomalie avec son numéro de ligne ; rien n'est écrit
     * tant que le fichier n'est pas entièrement valide.
     */
    @PostMapping(value = "/{kind}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ImportReportResponse importFile(@PathVariable String kind,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) UUID centerId,
                                           @RequestParam(defaultValue = "true") boolean dryRun,
                                           HttpServletRequest http) {
        CentreId center =centerAccessGuard.requireCenter(centerId);
        ReferentialKind referential = catalogue.fromSlug(kind);
        tracer(http, referential, dryRun ? "vérification d'import" : "import");
        ImportTable table;
        try {
            table = fileReader.read(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new BusinessException("IMPORT_UNREADABLE_FILE", "Le fichier n'a pas pu être lu.");
        }
        return ImportReportResponse.from(service.importEntries(center, referential, table, dryRun));
    }

    /**
     * Modèle d'import : en-têtes attendus et ligne d'exemple ({@code format=csv|xlsx}).
     */
    @GetMapping("/{kind}/template")
    public ResponseEntity<byte[]> template(@PathVariable String kind,
                                           @RequestParam(defaultValue = "xlsx") String format) {
        ReferentialKind referential = catalogue.fromSlug(kind);
        boolean csv = "csv".equalsIgnoreCase(format);
        byte[] body = csv ? templateWriter.csv(referential) : templateWriter.xlsx(referential);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(csv ? new MediaType("text", "csv", StandardCharsets.UTF_8) : XLSX);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("modele-" + referential.slug() + (csv ? ".csv" : ".xlsx"), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(body);
    }
}

