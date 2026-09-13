package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.port.PrescriptionMedicaleUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.request.PrescriptionMedicaleSearchRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertPrescriptionMedicaleRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.EntityWriteResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.PrescriptionMedicaleResponse;
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
 * Prescriptions médicales du patient : cibles de dialyse et traitement de l'anémie (EPO, fer injectable).
 * <p>
 * Accès réservé au corps médical : le MEDECIN écrit, l'ADMIN consulte.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PrescriptionMedicaleRestController {

    private final PrescriptionMedicaleUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;

    public PrescriptionMedicaleRestController(PrescriptionMedicaleUseCase useCase,
                                              CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/prescriptions")
    public ResponseEntity<PagedResponse<PrescriptionMedicaleResponse>> list(
            @PathVariable UUID patientId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        var paged = useCase.listPagedByPatient(center, patientId, from, to, page, size);
        return ResponseEntity.ok(PagedResponse.from(paged, PrescriptionMedicaleResponse::from));
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @PostMapping("/prescriptions/search")
    public ResponseEntity<PagedResponse<PrescriptionMedicaleResponse>> searchPrescriptions(
            @RequestBody @Valid PrescriptionMedicaleSearchRequest criteria) {
        CenterId center = centerAccessGuard.requireCenter(criteria.centerId());
        var paged = useCase.listPagedByPatient(
                center,
                criteria.patientId(),
                criteria.dateFrom(),
                criteria.dateTo(),
                criteria.page(),
                criteria.size());
        return ResponseEntity.ok(PagedResponse.from(paged, PrescriptionMedicaleResponse::from));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PostMapping("/{patientId}/prescriptions")
    public ResponseEntity<EntityWriteResponse> create(@PathVariable UUID patientId,
                                                      @RequestBody @Valid UpsertPrescriptionMedicaleRequest request) {
        return ResponseEntity.ok(save(patientId, null, request));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @PutMapping("/{patientId}/prescriptions/{prescriptionId}")
    public ResponseEntity<EntityWriteResponse> update(@PathVariable UUID patientId,
                                                      @PathVariable UUID prescriptionId,
                                                      @RequestBody @Valid UpsertPrescriptionMedicaleRequest request) {
        return ResponseEntity.ok(save(patientId, prescriptionId, request));
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @DeleteMapping("/{patientId}/prescriptions/{prescriptionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID patientId,
                                       @PathVariable UUID prescriptionId,
                                       @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        useCase.delete(center, patientId, prescriptionId);
        return ResponseEntity.noContent().build();
    }

    private EntityWriteResponse save(UUID patientId, UUID prescriptionId, UpsertPrescriptionMedicaleRequest request) {
        CenterId center = centerAccessGuard.requireCenter(request.centerId());
        var prescription = useCase.save(
                center,
                patientId,
                prescriptionId,
                request.datePrescription(),
                request.medecinId(),
                request.qbCible(),
                request.qdCible(),
                request.ufMaxMl(),
                request.dureeCibleMin(),
                request.typeDialyseurPrescrit(),
                request.anticoagTypePrescrit(),
                request.epoMolecule(),
                request.epoDoseUi(),
                request.epoVoie(),
                request.epoFrequence(),
                request.ferMolecule(),
                request.ferDoseMg(),
                request.ferVoie(),
                request.ferFrequence()
        );
        return new EntityWriteResponse(prescription.getId(), prescription.getPatientId(), prescription.getUpdatedAt());
    }
}
