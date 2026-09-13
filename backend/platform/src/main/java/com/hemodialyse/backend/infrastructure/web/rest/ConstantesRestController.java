package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.query.ConstantesQueryService;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.response.ConstanteSeanceResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.function.Function;

/**
 * Vue longitudinale des constantes du patient (poids, tension, débit, UF, durée), agrégée à
 * partir du volet paramédical déjà saisi séance après séance. Lecture seule.
 * <p>
 * Accès réservé au corps médical (MEDECIN et ADMIN).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class ConstantesRestController {

    private final ConstantesQueryService queryService;
    private final CenterAccessGuard centerAccessGuard;

    public ConstantesRestController(ConstantesQueryService queryService, CenterAccessGuard centerAccessGuard) {
        this.queryService = queryService;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/constantes")
    public ResponseEntity<PagedResponse<ConstanteSeanceResponse>> list(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        var paged = queryService.getConstantes(center.value(), patientId, page, size);
        return ResponseEntity.ok(PagedResponse.from(paged, Function.identity()));
    }
}
