package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.model.ResultatAnalyse;
import com.hemodialyse.backend.domain.seance.port.ResultatAnalyseUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.ResultatAnalyseSearchRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertResultatAnalyseRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.EntityWriteResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.ResultatAnalyseResponse;
import jakarta.validation.Valid;
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

import java.time.LocalDate;
import java.util.UUID;

/**
 * Résultats d'analyses biologiques du patient : NFS, bilan martial, adéquation de dialyse,
 * bilan phospho-calcique, nutrition et inflammation.
 * <p>
 * Accès réservé au corps médical : le MEDECIN écrit, l'ADMIN consulte.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class ResultatAnalyseRestController {

    private final ResultatAnalyseUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;

    public ResultatAnalyseRestController(ResultatAnalyseUseCase useCase,
                                         CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/analyses")
    public ResponseEntity<PagedResponse<ResultatAnalyseResponse>> list(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        var paged = useCase.listPagedByPatient(center, patientId, from, to, page, size);
        return ResponseEntity.ok(PagedResponse.from(paged, ResultatAnalyseResponse::from));
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @PostMapping("/analyses/search")
    public ResponseEntity<PagedResponse<ResultatAnalyseResponse>> searchAnalyses(
            @RequestBody @Valid ResultatAnalyseSearchRequest criteria) {
        CenterId center = centerAccessGuard.requireCenter(criteria.centerId());
        var paged = useCase.listPagedByPatient(
                center,
                criteria.patientId(),
                criteria.dateFrom(),
                criteria.dateTo(),
                criteria.page(),
                criteria.size());
        return ResponseEntity.ok(PagedResponse.from(paged, ResultatAnalyseResponse::from));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/analyses")
    public ResponseEntity<EntityWriteResponse> create(@PathVariable UUID patientId,
                                                      @RequestBody @Valid UpsertResultatAnalyseRequest request) {
        return ResponseEntity.ok(toWriteResponse(saveInternal(patientId, null, request)));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/analyses/{analyseId}")
    public ResponseEntity<EntityWriteResponse> update(@PathVariable UUID patientId,
                                                      @PathVariable UUID analyseId,
                                                      @RequestBody @Valid UpsertResultatAnalyseRequest request) {
        return ResponseEntity.ok(toWriteResponse(saveInternal(patientId, analyseId, request)));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/analyses/{analyseId}")
    public ResponseEntity<Void> delete(@PathVariable UUID patientId,
                                       @PathVariable UUID analyseId,
                                       @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        useCase.delete(center, patientId, analyseId);
        return ResponseEntity.noContent().build();
    }

    private ResultatAnalyse saveInternal(UUID patientId, UUID analyseId, UpsertResultatAnalyseRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        return useCase.save(
                center,
                patientId,
                analyseId,
                request.datePrelevement(),
                request.hbGDl(),
                request.htPct(),
                request.plaquettes(),
                request.ferritineNgMl(),
                request.cstfPct(),
                request.epoEndogeneMuiMl(),
                request.ureePreMgDl(),
                request.ureePostMgDl(),
                request.creatinineMgDl(),
                request.ktVMensuel(),
                request.phosphoreMgDl(),
                request.calciumMgDl(),
                request.pthPgMl(),
                request.albumineGDl(),
                request.proteinesGDl(),
                request.crpMgL()
        );
    }

    private EntityWriteResponse toWriteResponse(ResultatAnalyse a) {
        return new EntityWriteResponse(a.getId(), a.getPatientId(), a.getUpdatedAt());
    }
}
