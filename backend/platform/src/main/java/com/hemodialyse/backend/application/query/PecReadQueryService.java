package com.hemodialyse.backend.application.query;

import com.hemodialyse.backend.infrastructure.persistence.entity.AttestationJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.PatientJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.PecJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.AttestationJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.PatientJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.PecJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.spec.AttestationSpecifications;
import com.hemodialyse.backend.infrastructure.persistence.spec.PecSpecifications;
import com.hemodialyse.backend.infrastructure.web.dto.request.AttestationSearchRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.PecSearchRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application read-model service used by controllers.
 */
@Service
public class PecReadQueryService {

    private final AttestationJpaRepository attestationRepository;
    private final PecJpaRepository pecRepository;
    private final PatientJpaRepository patientRepository;

    public PecReadQueryService(AttestationJpaRepository attestationRepository,
                               PecJpaRepository pecRepository,
                               PatientJpaRepository patientRepository) {
        this.attestationRepository = attestationRepository;
        this.pecRepository = pecRepository;
        this.patientRepository = patientRepository;
    }

    public List<Map<String, Object>> listAttestationsByCenter(UUID centerId) {
        AttestationSearchRequest request = new AttestationSearchRequest(centerId, 0, 200, null, null, null, null, null, null);
        return listAttestationsByCenterPaged(request).items();
    }

    public List<Map<String, Object>> listPecByCenterDetailed(UUID centerId) {
        PecSearchRequest request = new PecSearchRequest(centerId, 0, 200, null, null, null, null, null, null, null);
        return listPecByCenterDetailedPaged(request).items();
    }

    public PageResult listAttestationsByCenterPaged(AttestationSearchRequest request) {
        int page = request.page();
        int size = request.size();

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("dateFin")));
        var result = attestationRepository.findAll(AttestationSpecifications.from(request), pageable);

        Map<UUID, PatientJpaEntity> patients = loadPatients(result.getContent().stream().map(AttestationJpaEntity::getPatientId).toList());
        List<Map<String, Object>> items = new ArrayList<>();

        for (AttestationJpaEntity a : result.getContent()) {
            PatientJpaEntity p = patients.get(a.getPatientId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", a.getId());
            row.put("patient_id", a.getPatientId());
            row.put("date_debut", a.getDateDebut());
            row.put("date_fin", a.getDateFin());
            row.put("code_patient", p != null ? p.getCodePatient() : null);
            row.put("nom", p != null ? p.getNom() : null);
            row.put("prenom", p != null ? p.getPrenom() : null);
            row.put("numero_assurance", p != null ? p.getNumeroAssurance() : null);
            items.add(row);
        }

        return new PageResult(items, result.getTotalElements(), page, size);
    }

    public PageResult listPecByCenterDetailedPaged(PecSearchRequest request) {
        int page = request.page();
        int size = request.size();

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("dateFinDemande")));
        var result = pecRepository.findAll(PecSpecifications.from(request), pageable);

        Map<UUID, PatientJpaEntity> patients = loadPatients(result.getContent().stream().map(PecJpaEntity::getPatientId).toList());
        List<Map<String, Object>> items = new ArrayList<>();

        for (PecJpaEntity pc : result.getContent()) {
            PatientJpaEntity p = patients.get(pc.getPatientId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", pc.getId());
            row.put("patient_id", pc.getPatientId());
            row.put("date_debut_demande", pc.getDateDebutDemande());
            row.put("date_fin_demande", pc.getDateFinDemande());
            row.put("statut", pc.getStatut());
            row.put("date_debut_effectif", pc.getDateDebutEffectif());
            row.put("date_fin_effectif", pc.getDateFinEffectif());
            row.put("code_patient", p != null ? p.getCodePatient() : null);
            row.put("nom", p != null ? p.getNom() : null);
            row.put("prenom", p != null ? p.getPrenom() : null);
            row.put("numero_assurance", p != null ? p.getNumeroAssurance() : null);
            items.add(row);
        }

        return new PageResult(items, result.getTotalElements(), page, size);
    }

    private Map<UUID, PatientJpaEntity> loadPatients(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return patientRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(PatientJpaEntity::getId, Function.identity(), (left, right) -> left));
    }

    public record PageResult(List<Map<String, Object>> items, long total, int page, int size) {
    }
}

