package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.model.PrescriptionMedicale;
import com.hemodialyse.backend.domain.seance.port.PrescriptionMedicaleUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.PrescriptionMedicaleSearchRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertPrescriptionMedicaleRequest;
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
public class PrescriptionMedicaleRestController {

    private final PrescriptionMedicaleUseCase useCase;

    public PrescriptionMedicaleRestController(PrescriptionMedicaleUseCase useCase) {
        this.useCase = useCase;
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/{patientId}/prescriptions")
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
    @PostMapping("/prescriptions/search")
    public ResponseEntity<?> searchPrescriptions(@RequestBody @Valid PrescriptionMedicaleSearchRequest criteria) {
        var items = listPrescriptionsByCriteria(criteria);
        return ResponseEntity.ok(items);
    }

    private java.util.List<?> listPrescriptionsByCriteria(PrescriptionMedicaleSearchRequest criteria) {
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
    @PostMapping("/{patientId}/prescriptions")
    public ResponseEntity<?> create(@PathVariable UUID patientId,
                                    @RequestBody @Valid UpsertPrescriptionMedicaleRequest request) {
        var prescription = useCase.save(
                CenterId.of(request.centerId()),
                patientId,
                null,
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
        return ResponseEntity.ok(Map.of(
                "id", prescription.getId(),
                "patientId", prescription.getPatientId(),
                "updatedAt", prescription.getUpdatedAt()
        ));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @PutMapping("/{patientId}/prescriptions/{prescriptionId}")
    public ResponseEntity<?> update(@PathVariable UUID patientId,
                                    @PathVariable UUID prescriptionId,
                                    @RequestBody @Valid UpsertPrescriptionMedicaleRequest request) {
        var prescription = useCase.save(
                CenterId.of(request.centerId()),
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
        return ResponseEntity.ok(Map.of(
                "id", prescription.getId(),
                "patientId", prescription.getPatientId(),
                "updatedAt", prescription.getUpdatedAt()
        ));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    @DeleteMapping("/{patientId}/prescriptions/{prescriptionId}")
    public ResponseEntity<?> delete(@PathVariable UUID patientId,
                                    @PathVariable UUID prescriptionId,
                                    @RequestParam UUID centerId) {
        useCase.delete(CenterId.of(centerId), patientId, prescriptionId);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toResponse(PrescriptionMedicale p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("patientId", p.getPatientId());
        m.put("centerId", p.getCenterId());
        m.put("datePrescription", p.getDatePrescription());
        m.put("medecinId", p.getMedecinId());
        m.put("qbCible", p.getQbCible());
        m.put("qdCible", p.getQdCible());
        m.put("ufMaxMl", p.getUfMaxMl());
        m.put("dureeCibleMin", p.getDureeCibleMin());
        m.put("typeDialyseurPrescrit", p.getTypeDialyseurPrescrit());
        m.put("anticoagTypePrescrit", p.getAnticoagTypePrescrit());
        m.put("epoMolecule", p.getEpoMolecule());
        m.put("epoDoseUi", p.getEpoDoseUi());
        m.put("epoVoie", p.getEpoVoie());
        m.put("epoFrequence", p.getEpoFrequence());
        m.put("ferMolecule", p.getFerMolecule());
        m.put("ferDoseMg", p.getFerDoseMg());
        m.put("ferVoie", p.getFerVoie());
        m.put("ferFrequence", p.getFerFrequence());
        m.put("createdAt", p.getCreatedAt());
        m.put("updatedAt", p.getUpdatedAt());
        return m;
    }
}


