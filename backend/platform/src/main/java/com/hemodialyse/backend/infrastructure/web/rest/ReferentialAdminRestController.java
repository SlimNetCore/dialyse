package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.referential.ReferentialAdminApplicationService;
import com.hemodialyse.backend.domain.referential.admin.model.ImportTable;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.importer.ReferentialTemplateWriter;
import com.hemodialyse.backend.infrastructure.importer.TabularFileReader;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.referential.ImportReportResponse;
import com.hemodialyse.backend.infrastructure.web.dto.referential.ReferentialEntryResponse;
import com.hemodialyse.backend.infrastructure.web.dto.referential.ReferentialKindResponse;
import com.hemodialyse.backend.infrastructure.web.dto.referential.UpsertReferentialEntryRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Administration des référentiels du centre : forfaits, créneaux, salles, médecins traitants, générateurs,
 * caisses, agences, centres payeurs, transporteurs — saisie, import CSV/Excel vérifié, modèles d'import.
 * <p>
 * Réservé à l'ADMIN (son centre, jamais un autre) et au SUPERADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/referentials")
@PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
public class ReferentialAdminRestController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReferentialAdminApplicationService service;
    private final CenterAccessGuard centerAccessGuard;
    private final TabularFileReader fileReader;
    private final ReferentialTemplateWriter templateWriter;

    public ReferentialAdminRestController(ReferentialAdminApplicationService service,
                                          CenterAccessGuard centerAccessGuard,
                                          TabularFileReader fileReader,
                                          ReferentialTemplateWriter templateWriter) {
        this.service = service;
        this.centerAccessGuard = centerAccessGuard;
        this.fileReader = fileReader;
        this.templateWriter = templateWriter;
    }

    /**
     * Référentiels administrables, dans l'ordre d'import conseillé.
     */
    @GetMapping
    public List<ReferentialKindResponse> kinds() {
        return Arrays.stream(ReferentialKind.values())
                .sorted(Comparator.comparingInt(ReferentialKind::importOrder))
                .map(ReferentialKindResponse::from)
                .toList();
    }

    @GetMapping("/{kind}")
    public PagedResponse<ReferentialEntryResponse> list(@PathVariable String kind,
                                                        @RequestParam(required = false) UUID centerId,
                                                        @RequestParam(required = false) String search,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        return PagedResponse.from(service.list(center, ReferentialKind.fromSlug(kind), search, page, size),
                ReferentialEntryResponse::from);
    }

    @PostMapping("/{kind}")
    public ResponseEntity<ReferentialEntryResponse> create(@PathVariable String kind,
                                                           @RequestBody @Valid UpsertReferentialEntryRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        var created = service.create(center, ReferentialKind.fromSlug(kind), request.values());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReferentialEntryResponse.from(created));
    }

    @PutMapping("/{kind}/{id}")
    public ReferentialEntryResponse update(@PathVariable String kind, @PathVariable UUID id,
                                           @RequestBody @Valid UpsertReferentialEntryRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        return ReferentialEntryResponse.from(service.update(center, ReferentialKind.fromSlug(kind), id, request.values()));
    }

    @DeleteMapping("/{kind}/{id}")
    public ResponseEntity<Void> delete(@PathVariable String kind, @PathVariable UUID id,
                                       @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        service.delete(center, ReferentialKind.fromSlug(kind), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Vérifie ({@code dryRun=true}, par défaut) ou importe un fichier CSV / Excel. Le compte rendu liste les
     * colonnes manquantes, les colonnes ignorées et chaque anomalie avec son numéro de ligne ; rien n'est écrit
     * tant que le fichier n'est pas entièrement valide.
     */
    @PostMapping(value = "/{kind}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportReportResponse importFile(@PathVariable String kind,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) UUID centerId,
                                           @RequestParam(defaultValue = "true") boolean dryRun) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        ReferentialKind referential = ReferentialKind.fromSlug(kind);
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
        ReferentialKind referential = ReferentialKind.fromSlug(kind);
        boolean csv = "csv".equalsIgnoreCase(format);
        byte[] body = csv ? templateWriter.csv(referential) : templateWriter.xlsx(referential);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(csv ? new MediaType("text", "csv", StandardCharsets.UTF_8) : XLSX);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("modele-" + referential.slug() + (csv ? ".csv" : ".xlsx"), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(body);
    }
}

