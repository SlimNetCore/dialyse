package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.medical.examen.aggregate.DemandeExamen;
import com.hemodialyse.backend.domain.medical.examen.entity.LigneDemandeExamen;
import com.hemodialyse.backend.domain.medical.examen.port.DemandeExamenRepositoryPort;
import com.hemodialyse.backend.domain.medical.examen.valueobject.CategorieExamen;
import com.hemodialyse.backend.domain.medical.examen.valueobject.StatutDemandeExamen;
import com.hemodialyse.backend.domain.medical.shared.valueobject.CodingSystem;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.DemandeExamenJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.LigneDemandeExamenJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.DemandeExamenJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.LigneDemandeExamenJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * L'agrégat {@code DemandeExamen} n'a pas de relation JPA mappée vers ses lignes (convention du
 * dépôt — FK brute, jointure manuelle) : cet adapter charge/persiste explicitement le parent et
 * ses lignes dans la même transaction, ce qui matérialise la frontière transactionnelle de
 * l'agrégat au niveau infrastructure.
 */
@Component
public class DemandeExamenRepositoryAdapter implements DemandeExamenRepositoryPort {

    private final DemandeExamenJpaRepository demandeJpa;
    private final LigneDemandeExamenJpaRepository ligneJpa;

    public DemandeExamenRepositoryAdapter(DemandeExamenJpaRepository demandeJpa,
                                          LigneDemandeExamenJpaRepository ligneJpa) {
        this.demandeJpa = demandeJpa;
        this.ligneJpa = ligneJpa;
    }

    @Override
    public PagedResult<DemandeExamen> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size) {
        Page<DemandeExamenJpaEntity> result = demandeJpa.findByPatientIdAndCenterId(
                patientId, centerId.value(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateDemande")));
        return PagedResult.of(result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalElements(), page, size);
    }

    @Override
    public Optional<DemandeExamen> findById(UUID id, UUID patientId, CenterId centerId) {
        return demandeJpa.findByIdAndPatientIdAndCenterId(id, patientId, centerId.value()).map(this::toDomain);
    }

    @Override
    @Transactional
    public DemandeExamen save(DemandeExamen demande) {
        DemandeExamenJpaEntity saved = demandeJpa.save(toJpa(demande));
        ligneJpa.deleteByDemandeId(saved.getId());
        for (LigneDemandeExamen ligne : demande.getLignes()) {
            ligneJpa.save(toLigneJpa(saved.getId(), ligne));
        }
        return toDomain(saved, demande.getLignes());
    }

    private DemandeExamen toDomain(DemandeExamenJpaEntity e) {
        List<LigneDemandeExamen> lignes = ligneJpa.findByDemandeId(e.getId()).stream()
                .map(this::toLigneDomain)
                .toList();
        return toDomain(e, lignes);
    }

    private DemandeExamen toDomain(DemandeExamenJpaEntity e, List<LigneDemandeExamen> lignes) {
        return DemandeExamen.reconstituer(e.getId(), e.getPatientId(), e.getCenterId(), e.getPrescripteurId(),
                e.getDateDemande(), CategorieExamen.valueOf(e.getCategorie()), e.isUrgent(), e.getMotif(),
                StatutDemandeExamen.valueOf(e.getStatut()), e.getConclusion(), lignes, e.getCreatedAt(), e.getUpdatedAt());
    }

    private LigneDemandeExamen toLigneDomain(LigneDemandeExamenJpaEntity e) {
        ConceptCode analyte = e.getAnalyteCode() == null ? null
                : ConceptCode.of(CodingSystem.valueOf(e.getAnalyteCodeSystem()), e.getAnalyteCode(), e.getAnalyteCodeDisplay());
        return LigneDemandeExamen.reconstituer(e.getId(), analyte, e.getLibelle(), e.getCommentaire());
    }

    private DemandeExamenJpaEntity toJpa(DemandeExamen d) {
        DemandeExamenJpaEntity e = new DemandeExamenJpaEntity();
        e.setId(d.getId());
        e.setPatientId(d.getPatientId());
        e.setCenterId(d.getCenterId());
        e.setPrescripteurId(d.getPrescripteurId());
        e.setDateDemande(d.getDateDemande());
        e.setCategorie(d.getCategorie().name());
        e.setUrgent(d.isUrgent());
        e.setMotif(d.getMotif());
        e.setStatut(d.getStatut().name());
        e.setConclusion(d.getConclusion());
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        return e;
    }

    private LigneDemandeExamenJpaEntity toLigneJpa(UUID demandeId, LigneDemandeExamen ligne) {
        LigneDemandeExamenJpaEntity e = new LigneDemandeExamenJpaEntity();
        e.setId(ligne.getId());
        e.setDemandeId(demandeId);
        ConceptCode analyte = ligne.getAnalyte();
        e.setAnalyteCodeSystem(analyte == null ? null : analyte.system().name());
        e.setAnalyteCode(analyte == null ? null : analyte.code());
        e.setAnalyteCodeDisplay(analyte == null ? null : analyte.display());
        e.setLibelle(ligne.getLibelle());
        e.setCommentaire(ligne.getCommentaire());
        return e;
    }
}
