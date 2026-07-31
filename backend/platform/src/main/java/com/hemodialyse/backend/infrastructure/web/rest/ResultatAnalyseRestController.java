package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.model.ResultatAnalyse;
import com.hemodialyse.backend.domain.seance.port.ResultatAnalyseUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.ResultatAnalyseSearchRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertResultatAnalyseRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
public class ResultatAnalyseRestController {

    private final ResultatAnalyseUseCase useCase;

    public ResultatAnalyseRestController(ResultatAnalyseUseCase useCase) {
        this.useCase = useCase;
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/{patientId}/analyses")
    public ResponseEntity<?> list(@PathVariable UUID patientId,
                                  @RequestParam UUID centerId,
                                  @RequestParam(required = false) LocalDate from,
                                  @RequestParam(required = false) LocalDate to) {
        var items = useCase.listByPatient(CenterId.of(centerId), patientId, from, to).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(items);
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @PostMapping("/analyses/search")
    public ResponseEntity<?> searchAnalyses(@RequestBody @Valid ResultatAnalyseSearchRequest criteria) {
        var items = listAnalysesByCriteria(criteria);
        return ResponseEntity.ok(items);
    }

    private java.util.List<?> listAnalysesByCriteria(ResultatAnalyseSearchRequest criteria) {
        return useCase.listByPatient(
                        CenterId.of(criteria.centerId()),
                        criteria.patientId(),
                        criteria.dateFrom(),
                        criteria.dateTo()
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @PostMapping("/{patientId}/analyses")
    public ResponseEntity<?> create(@PathVariable UUID patientId,
                                    @RequestBody @Valid UpsertResultatAnalyseRequest request) {
        var analyse = saveInternal(patientId, null, request);
        return ResponseEntity.ok(toWriteResponse(analyse));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @PutMapping("/{patientId}/analyses/{analyseId}")
    public ResponseEntity<?> update(@PathVariable UUID patientId,
                                    @PathVariable UUID analyseId,
                                    @RequestBody @Valid UpsertResultatAnalyseRequest request) {
        var analyse = saveInternal(patientId, analyseId, request);
        return ResponseEntity.ok(toWriteResponse(analyse));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @DeleteMapping("/{patientId}/analyses/{analyseId}")
    public ResponseEntity<?> delete(@PathVariable UUID patientId,
                                    @PathVariable UUID analyseId,
                                    @RequestParam UUID centerId) {
        useCase.delete(CenterId.of(centerId), patientId, analyseId);
        return ResponseEntity.noContent().build();
    }

    private ResultatAnalyse saveInternal(UUID patientId, UUID analyseId, UpsertResultatAnalyseRequest request) {
        return useCase.save(
                CenterId.of(request.centerId()),
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

    private Map<String, Object> toResponse(ResultatAnalyse a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("patientId", a.getPatientId());
        m.put("centerId", a.getCenterId());
        m.put("datePrelevement", a.getDatePrelevement());
        m.put("hbGDl", a.getHbGDl());
        m.put("htPct", a.getHtPct());
        m.put("plaquettes", a.getPlaquettes());
        m.put("ferritineNgMl", a.getFerritineNgMl());
        m.put("cstfPct", a.getCstfPct());
        m.put("epoEndogeneMuiMl", a.getEpoEndogeneMuiMl());
        m.put("ureePreMgDl", a.getUreePreMgDl());
        m.put("ureePostMgDl", a.getUreePostMgDl());
        m.put("creatinineMgDl", a.getCreatinineMgDl());
        m.put("ktVMensuel", a.getKtVMensuel());
        m.put("phosphoreMgDl", a.getPhosphoreMgDl());
        m.put("calciumMgDl", a.getCalciumMgDl());
        m.put("pthPgMl", a.getPthPgMl());
        m.put("albumineGDl", a.getAlbumineGDl());
        m.put("proteinesGDl", a.getProteinesGDl());
        m.put("crpMgL", a.getCrpMgL());
        m.put("createdAt", a.getCreatedAt());
        m.put("updatedAt", a.getUpdatedAt());
        return m;
    }

    private Map<String, Object> toWriteResponse(ResultatAnalyse a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("patientId", a.getPatientId());
        m.put("updatedAt", a.getUpdatedAt());
        return m;
    }
}



