package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.query.PatientStatsQueryService;
import com.hemodialyse.backend.infrastructure.web.dto.request.PatientStatsSearchRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientStatsRestController {

    private final PatientStatsQueryService statsQueryService;

    public PatientStatsRestController(PatientStatsQueryService statsQueryService) {
        this.statsQueryService = statsQueryService;
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/{patientId}/stats/paramedical")
    public ResponseEntity<?> paramedical(@PathVariable UUID patientId,
                                         @RequestParam UUID centerId,
                                         @RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            return ResponseEntity.badRequest().body(Map.of("error", "from doit être <= to", "code", "INVALID_DATE_RANGE"));
        }
        return ResponseEntity.ok(statsQueryService.getParamedicalStats(centerId, patientId, from, to));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @PostMapping("/stats/paramedical/search")
    public ResponseEntity<?> searchParamedicalStats(@RequestBody @Valid PatientStatsSearchRequest criteria) {
        if (criteria.dateFrom() != null && criteria.dateTo() != null && criteria.dateFrom().isAfter(criteria.dateTo())) {
            return ResponseEntity.badRequest().body(Map.of("error", "dateFrom doit être <= dateTo", "code", "INVALID_DATE_RANGE"));
        }
        return ResponseEntity.ok(statsQueryService.getParamedicalStats(criteria.centerId(), criteria.patientId(), criteria.dateFrom(), criteria.dateTo()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/{patientId}/stats/medical")
    public ResponseEntity<?> medical(@PathVariable UUID patientId,
                                     @RequestParam UUID centerId,
                                     @RequestParam(required = false) LocalDate from,
                                     @RequestParam(required = false) LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            return ResponseEntity.badRequest().body(Map.of("error", "from doit être <= to", "code", "INVALID_DATE_RANGE"));
        }
        return ResponseEntity.ok(statsQueryService.getMedicalStats(centerId, patientId, from, to));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @PostMapping("/stats/medical/search")
    public ResponseEntity<?> searchMedicalStats(@RequestBody @Valid PatientStatsSearchRequest criteria) {
        if (criteria.dateFrom() != null && criteria.dateTo() != null && criteria.dateFrom().isAfter(criteria.dateTo())) {
            return ResponseEntity.badRequest().body(Map.of("error", "dateFrom doit être <= dateTo", "code", "INVALID_DATE_RANGE"));
        }
        return ResponseEntity.ok(statsQueryService.getMedicalStats(criteria.centerId(), criteria.patientId(), criteria.dateFrom(), criteria.dateTo()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{patientId}/stats/export")
    public ResponseEntity<?> export(@PathVariable UUID patientId,
                                    @RequestParam UUID centerId,
                                    @RequestParam String format,
                                    @RequestParam(required = false) LocalDate from,
                                    @RequestParam(required = false) LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            return ResponseEntity.badRequest().body(Map.of("error", "from doit être <= to", "code", "INVALID_DATE_RANGE"));
        }

        String normalized = format == null ? "" : format.trim().toLowerCase();
        if ("csv".equals(normalized)) {
            String csv = statsQueryService.exportCsv(centerId, patientId, from, to);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=patient-stats-" + patientId + ".csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        }

        if ("pdf".equals(normalized)) {
            byte[] pdf = statsQueryService.exportPdf(centerId, patientId, from, to);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=patient-stats-" + patientId + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        }

        return ResponseEntity.badRequest().body(Map.of("error", "format doit être pdf ou csv", "code", "INVALID_EXPORT_FORMAT"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/stats/export/search")
    public ResponseEntity<?> searchExportStats(@RequestBody @Valid PatientStatsSearchRequest criteria) {
        if (criteria.dateFrom() != null && criteria.dateTo() != null && criteria.dateFrom().isAfter(criteria.dateTo())) {
            return ResponseEntity.badRequest().body(Map.of("error", "dateFrom doit être <= dateTo", "code", "INVALID_DATE_RANGE"));
        }

        String normalized = criteria.format() == null ? "" : criteria.format().trim().toLowerCase();
        if ("csv".equals(normalized)) {
            String csv = statsQueryService.exportCsv(criteria.centerId(), criteria.patientId(), criteria.dateFrom(), criteria.dateTo());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=patient-stats-" + criteria.patientId() + ".csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        }

        if ("pdf".equals(normalized)) {
            byte[] pdf = statsQueryService.exportPdf(criteria.centerId(), criteria.patientId(), criteria.dateFrom(), criteria.dateTo());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=patient-stats-" + criteria.patientId() + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        }

        return ResponseEntity.badRequest().body(Map.of("error", "format doit être pdf ou csv", "code", "INVALID_EXPORT_FORMAT"));
    }
}






