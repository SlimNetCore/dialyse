package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.migration.MigrationApplicationService;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.referential.admin.model.ImportTable;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.importer.MigrationTemplateWriter;
import com.hemodialyse.backend.infrastructure.importer.TabularFileReader;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.migration.MigrationDtos.BatchDetailResponse;
import com.hemodialyse.backend.infrastructure.web.dto.migration.MigrationDtos.BatchResponse;
import com.hemodialyse.backend.infrastructure.web.dto.migration.MigrationDtos.EntityResponse;
import com.hemodialyse.backend.infrastructure.web.dto.migration.MigrationDtos.OpenBatchRequest;
import com.hemodialyse.backend.infrastructure.web.dto.migration.MigrationDtos.RunResponse;
import com.hemodialyse.backend.infrastructure.web.dto.migration.MigrationDtos.ValueMappingRequest;
import com.hemodialyse.backend.infrastructure.web.dto.migration.MigrationDtos.ValueMappingResponse;
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
import java.security.Principal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Reprise des données d'un système existant (assurés, patients, affectations…), par lot et par centre :
 * modèles de fichiers, vérification, import rejouable, correspondances de valeurs, clôture et annulation.
 * <p>
 * Réservé à l'ADMIN (son centre uniquement) et au SUPERADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/migration")
@PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
public class MigrationRestController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final MigrationApplicationService service;
    private final CenterAccessGuard centerAccessGuard;
    private final TabularFileReader fileReader;
    private final MigrationTemplateWriter templateWriter;

    public MigrationRestController(MigrationApplicationService service, CenterAccessGuard centerAccessGuard,
                                   TabularFileReader fileReader, MigrationTemplateWriter templateWriter) {
        this.service = service;
        this.centerAccessGuard = centerAccessGuard;
        this.fileReader = fileReader;
        this.templateWriter = templateWriter;
    }

    private static String user(Principal principal) {
        return principal != null ? principal.getName() : null;
    }

    /**
     * Données reprises, dans l'ordre de chargement.
     */
    @GetMapping("/entities")
    public List<EntityResponse> entities() {
        return Arrays.stream(MigrationEntity.values())
                .sorted(Comparator.comparingInt(MigrationEntity::order))
                .map(EntityResponse::from)
                .toList();
    }

    @GetMapping("/entities/{entity}/template")
    public ResponseEntity<byte[]> template(@PathVariable String entity, @RequestParam(defaultValue = "xlsx") String format) {
        MigrationEntity e = MigrationEntity.fromSlug(entity);
        boolean csv = "csv".equalsIgnoreCase(format);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(csv ? new MediaType("text", "csv", StandardCharsets.UTF_8) : XLSX);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("reprise-" + e.slug() + (csv ? ".csv" : ".xlsx"), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(csv ? templateWriter.csv(e) : templateWriter.xlsx(e));
    }

    @GetMapping("/batches")
    public PagedResponse<BatchResponse> batches(@RequestParam(required = false) UUID centerId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.from(service.list(centerAccessGuard.requireCenter(centerId), page, size), BatchResponse::from);
    }

    @PostMapping("/batches")
    public ResponseEntity<BatchResponse> open(@RequestBody @Valid OpenBatchRequest request, Principal principal) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        var batch = service.open(center, request.libelle(), request.sourceSystem(), request.dateDebutReprise(), user(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(BatchResponse.from(batch));
    }

    @GetMapping("/batches/{batchId}")
    public BatchDetailResponse batch(@PathVariable UUID batchId, @RequestParam(required = false) UUID centerId) {
        return BatchDetailResponse.from(service.get(centerAccessGuard.requireCenter(centerId), batchId));
    }

    /**
     * Vérifie ({@code dryRun=true}, par défaut) ou importe un fichier CSV / Excel. Rien n'est écrit tant que le
     * fichier contient une anomalie ; le compte rendu est conservé dans l'historique du lot.
     */
    @PostMapping(value = "/batches/{batchId}/import/{entity}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RunResponse importFile(@PathVariable UUID batchId, @PathVariable String entity,
                                  @RequestParam("file") MultipartFile file,
                                  @RequestParam(required = false) UUID centerId,
                                  @RequestParam(defaultValue = "true") boolean dryRun,
                                  Principal principal) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        MigrationEntity e = MigrationEntity.fromSlug(entity);
        ImportTable table;
        try {
            table = fileReader.read(file.getOriginalFilename(), file.getBytes());
        } catch (IOException ex) {
            throw new BusinessException("IMPORT_UNREADABLE_FILE", "Le fichier n'a pas pu être lu.");
        }
        return RunResponse.from(service.importEntity(center, batchId, e, file.getOriginalFilename(), table, dryRun, user(principal)));
    }

    @PostMapping("/batches/{batchId}/close")
    public BatchResponse close(@PathVariable UUID batchId, @RequestParam(required = false) UUID centerId) {
        return BatchResponse.from(service.close(centerAccessGuard.requireCenter(centerId), batchId));
    }

    @PostMapping("/batches/{batchId}/cancel")
    public BatchResponse cancel(@PathVariable UUID batchId, @RequestParam(required = false) UUID centerId) {
        return BatchResponse.from(service.cancel(centerAccessGuard.requireCenter(centerId), batchId));
    }

    @GetMapping("/value-mappings")
    public List<ValueMappingResponse> valueMappings(@RequestParam(required = false) UUID centerId) {
        return service.valueMappings(centerAccessGuard.requireCenter(centerId)).entrySet().stream()
                .flatMap(column -> column.getValue().entrySet().stream()
                        .map(v -> new ValueMappingResponse(column.getKey(), v.getKey(), v.getValue())))
                .sorted(Comparator.comparing(ValueMappingResponse::column).thenComparing(ValueMappingResponse::source))
                .toList();
    }

    @PutMapping("/value-mappings")
    public ResponseEntity<Void> saveValueMapping(@RequestBody @Valid ValueMappingRequest request) {
        service.saveValueMapping(centerAccessGuard.requireCenter(request.centerId()), request.column(), request.source(),
                request.target());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/value-mappings")
    public ResponseEntity<Void> deleteValueMapping(@RequestParam(required = false) UUID centerId,
                                                   @RequestParam String column, @RequestParam String source) {
        service.deleteValueMapping(centerAccessGuard.requireCenter(centerId), column, source);
        return ResponseEntity.noContent().build();
    }
}

