package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.facturation.port.*;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.FacturationPreviewRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.FacturationSettingsUpdateRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.FacturationValidateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facturation")
public class FacturationRestController {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final FacturationUseCase useCase;

    public FacturationRestController(FacturationUseCase useCase) {
        this.useCase = useCase;
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody @Valid FacturationPreviewRequest request) {
        FacturationPreviewResult result = useCase.preview(new FacturationPreviewQuery(
                CenterId.of(request.centerId()),
                parseMonth(request.month()),
                request.periodStart(),
                request.periodEnd(),
                request.regroupementMultiForfait()
        ));
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody @Valid FacturationValidateRequest request) {
        FacturationValidationResult result = useCase.validate(new FacturationValidateCommand(
                CenterId.of(request.centerId()),
                request.userId(),
                parseMonth(request.month()),
                request.periodStart(),
                request.periodEnd(),
                request.regroupementMultiForfait(),
                request.previewGeneratedAt()
        ));
        return ResponseEntity.ok(Map.of(
                "createdInvoices", result.createdInvoices(),
                "billedSeances", result.billedSeances()
        ));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN','SECRETAIRE')")
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(@RequestParam UUID centerId, @RequestParam String month) {
        FacturationDashboardResult result = useCase.dashboard(new FacturationDashboardQuery(
                CenterId.of(centerId),
                parseMonth(month)
        ));
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN','SECRETAIRE')")
    @GetMapping("/settings")
    public ResponseEntity<?> settings(@RequestParam UUID centerId) {
        var settings = useCase.getSettings(new FacturationSettingsQuery(CenterId.of(centerId)));
        return ResponseEntity.ok(settings);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestBody @Valid FacturationSettingsUpdateRequest request) {
        var updated = useCase.updateSettings(new FacturationSettingsCommand(
                CenterId.of(request.centerId()),
                request.userId(),
                request.codeFormat(),
                request.regroupementMultiForfait()
        ));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codeFormat", updated.codeFormat());
        body.put("regroupementMultiForfait", updated.regroupementMultiForfait());
        body.put("updatedAt", updated.updatedAt());
        return ResponseEntity.ok(body);
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        return YearMonth.parse(month.trim(), MONTH_FORMAT);
    }
}

