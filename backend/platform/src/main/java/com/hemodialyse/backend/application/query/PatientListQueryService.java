package com.hemodialyse.backend.application.query;

import com.hemodialyse.backend.infrastructure.persistence.entity.PatientJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.PecJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.PatientJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.PecJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.spec.PatientSpecifications;
import com.hemodialyse.backend.infrastructure.web.dto.request.PatientSearchRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Application-layer read model for patient list view.
 */
@Service
public class PatientListQueryService {

    private final PatientJpaRepository patientRepository;
    private final PecJpaRepository pecRepository;

    /**
     * Whitelist mapping a frontend column id (as sent by app-configurable-list) to
     * the actual sortable JPA entity property. Columns absent here (nonFacturable,
     * pecStatus, pecForfaitId, joursDialyse...) are derived/looked up AFTER this
     * query runs, so they can't be pushed down to SQL — sorting by them silently
     * falls back to the default order.
     */
    private static final Map<String, String> SORTABLE_COLUMNS = Map.ofEntries(
            Map.entry("code", "codePatient"),
            Map.entry("nom", "nom"),
            Map.entry("prenom", "prenom"),
            Map.entry("sexe", "sexe"),
            Map.entry("dateAdmission", "dateAdmission"),
            Map.entry("numeroAssurance", "numeroAssurance"),
            Map.entry("etatPatient", "etatPatient"),
            Map.entry("medecinTraitantId", "medecinTraitantId"),
            Map.entry("positionId", "positionId"),
            Map.entry("transporteurAllerId", "transporteurAllerId"),
            Map.entry("transporteurRetourId", "transporteurRetourId")
    );

    public PatientListQueryService(PatientJpaRepository patientRepository, PecJpaRepository pecRepository) {
        this.patientRepository = patientRepository;
        this.pecRepository = pecRepository;
    }

    public PageResult search(UUID centerId, PatientSearchRequest req) {
        int page = req.page();
        int size = req.size();

        var pageable = PageRequest.of(page, size, resolveSort(req.sortBy(), req.sortDirection()));
        var pageResult = patientRepository.findAll(PatientSpecifications.from(centerId, req), pageable);

        List<PatientJpaEntity> patients = pageResult.getContent();
        Map<UUID, PecJpaEntity> latestValidatedByPatient = loadLatestValidatedPecByPatient(centerId, patients);

        List<Map<String, Object>> items = new ArrayList<>();
        for (PatientJpaEntity p : patients) {
            PecJpaEntity pec = latestValidatedByPatient.get(p.getId());
            boolean nonFacturable = pec == null;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("codePatient", p.getCodePatient());
            row.put("nom", p.getNom());
            row.put("prenom", p.getPrenom());
            row.put("sexe", p.getSexe());
            row.put("dateAdmission", p.getDateAdmission());
            row.put("numeroAssurance", p.getNumeroAssurance());
            row.put("etatPatient", p.getEtatPatient());
            String dateEvt = p.getDateEvenementEtat() != null ? p.getDateEvenementEtat().toString() : "";
            row.put("dateEvenementEtat", dateEvt);
            row.put("dateEvenement", dateEvt);
            row.put("medecinTraitantId", p.getMedecinTraitantId());
            row.put("positionId", p.getPositionId());
            row.put("transporteurAllerId", p.getTransporteurAllerId());
            row.put("transporteurRetourId", p.getTransporteurRetourId());
            row.put("jourDimanche", p.getJourDimanche());
            row.put("jourLundi", p.getJourLundi());
            row.put("jourMardi", p.getJourMardi());
            row.put("jourMercredi", p.getJourMercredi());
            row.put("jourJeudi", p.getJourJeudi());
            row.put("jourVendredi", p.getJourVendredi());
            row.put("jourSamedi", p.getJourSamedi());
            row.put("nonFacturable", nonFacturable);
            row.put("pecStatus", pec != null ? pec.getStatut() : null);
            row.put("pecForfaitId", pec != null ? pec.getForfaitDemandeId() : null);
            items.add(row);
        }

        return new PageResult(items, pageResult.getTotalElements(), page, size);
    }

    private Sort resolveSort(String sortBy, String sortDirection) {
        String property = sortBy != null ? SORTABLE_COLUMNS.get(sortBy) : null;
        if (property == null) {
            return Sort.by(Sort.Order.asc("nom"), Sort.Order.asc("prenom"));
        }
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(new Sort.Order(direction, property));
    }

    private Map<UUID, PecJpaEntity> loadLatestValidatedPecByPatient(UUID centerId, List<PatientJpaEntity> patients) {
        if (patients.isEmpty()) return Map.of();

        List<UUID> ids = patients.stream().map(PatientJpaEntity::getId).toList();
        List<PecJpaEntity> pecs = pecRepository.findByCenterIdAndStatutAndPatientIdIn(centerId, "VALIDEE", ids);

        Map<UUID, PecJpaEntity> latestByPatient = new HashMap<>();
        for (PecJpaEntity pec : pecs) {
            latestByPatient.merge(
                    pec.getPatientId(),
                    pec,
                    (left, right) -> {
                        if (left.getCreatedAt() == null) return right;
                        if (right.getCreatedAt() == null) return left;
                        return Comparator.comparing(PecJpaEntity::getCreatedAt).compare(left, right) >= 0 ? left : right;
                    }
            );
        }
        return latestByPatient;
    }

    public record PageResult(List<Map<String, Object>> items, long total, int page, int size) {
    }
}
