package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.facturation.port.*;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.reporting.FacturationSyntheseMensuelleReportService;
import com.hemodialyse.backend.infrastructure.web.dto.request.FacturationSynthesePrintRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facturation")
public class FacturationRestController {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final FacturationUseCase useCase;
    private final FacturationSyntheseMensuelleReportService syntheseReportService;

    public FacturationRestController(FacturationUseCase useCase,
                                     FacturationSyntheseMensuelleReportService syntheseReportService) {
        this.useCase = useCase;
        this.syntheseReportService = syntheseReportService;
    }

    // POST /preview, /preview/seances/{id}/exclude|include|forfait : contexte facturation (SimulationControleur, slice F2)

    // POST /validate : contexte facturation (ValidationControleur, slice F3)

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN','SECRETAIRE')")
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(@RequestParam UUID centerId, @RequestParam String month) {
        FacturationDashboardResult result = useCase.dashboard(new FacturationDashboardQuery(
                CenterId.of(centerId),
                parseMonth(month)
        ));
        return ResponseEntity.ok(result);
    }

    // GET|PUT /settings : contexte facturation (ParametresFacturationControleur, slice F1)

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN','SECRETAIRE')")
    @PostMapping("/synthese/print")
    public ResponseEntity<byte[]> printSynthese(@RequestBody @Valid FacturationSynthesePrintRequest request) {
        try {
            byte[] data = syntheseReportService.generate(
                    request.centerId(),
                    request.periodStart(),
                    request.periodEnd(),
                    request.format()
            );
            return buildPrintResponse(data, request.format());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(e.getMessage().getBytes());
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Erreur impression synthese: " + e.getMessage()).getBytes());
        }
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        return YearMonth.parse(month.trim(), MONTH_FORMAT);
    }

    private ResponseEntity<byte[]> buildPrintResponse(byte[] data, String format) {
        String normalized = (format == null || format.isBlank()) ? "PDF" : format.trim().toUpperCase();
        HttpHeaders headers = new HttpHeaders();
        return switch (normalized) {
            case "EXCEL", "XLS", "XLSX" -> {
                headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=synthese-facturation-mensuelle.xlsx");
                yield ResponseEntity.ok().headers(headers).body(data);
            }
            case "HTML" -> {
                headers.setContentType(MediaType.TEXT_HTML);
                yield ResponseEntity.ok().headers(headers).body(data);
            }
            default -> {
                headers.setContentType(MediaType.APPLICATION_PDF);
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=synthese-facturation-mensuelle.pdf");
                yield ResponseEntity.ok().headers(headers).body(data);
            }
        };
    }
}

