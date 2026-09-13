package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.medical.ordonnance.aggregate.Ordonnance;
import com.hemodialyse.backend.domain.medical.ordonnance.entity.LigneOrdonnance;
import com.hemodialyse.backend.domain.medical.ordonnance.port.OrdonnanceRepositoryPort;
import com.hemodialyse.backend.domain.medical.ordonnance.valueobject.StatutOrdonnance;
import com.hemodialyse.backend.domain.medical.shared.valueobject.CodingSystem;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.LigneOrdonnanceJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.OrdonnanceJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.LigneOrdonnanceJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.OrdonnanceJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * L'agrégat {@code Ordonnance} n'a pas de relation JPA mappée vers ses lignes (convention du
 * dépôt — FK brute, jointure manuelle, cf. {@code DemandeExamenRepositoryAdapter}) : cet adapter
 * charge/persiste explicitement le parent et ses lignes dans la même transaction.
 */
@Component
public class OrdonnanceRepositoryAdapter implements OrdonnanceRepositoryPort {

    private final OrdonnanceJpaRepository ordonnanceJpa;
    private final LigneOrdonnanceJpaRepository ligneJpa;

    public OrdonnanceRepositoryAdapter(OrdonnanceJpaRepository ordonnanceJpa, LigneOrdonnanceJpaRepository ligneJpa) {
        this.ordonnanceJpa = ordonnanceJpa;
        this.ligneJpa = ligneJpa;
    }

    @Override
    public PagedResult<Ordonnance> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size) {
        Page<OrdonnanceJpaEntity> result = ordonnanceJpa.findByPatientIdAndCenterId(
                patientId, centerId.value(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "datePrescription")));
        return PagedResult.of(result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalElements(), page, size);
    }

    @Override
    public Optional<Ordonnance> findById(UUID id, UUID patientId, CenterId centerId) {
        return ordonnanceJpa.findByIdAndPatientIdAndCenterId(id, patientId, centerId.value()).map(this::toDomain);
    }

    @Override
    @Transactional
    public Ordonnance save(Ordonnance ordonnance) {
        OrdonnanceJpaEntity saved = ordonnanceJpa.save(toJpa(ordonnance));
        ligneJpa.deleteByOrdonnanceId(saved.getId());
        for (LigneOrdonnance ligne : ordonnance.getLignes()) {
            ligneJpa.save(toLigneJpa(saved.getId(), ligne));
        }
        return toDomain(saved, ordonnance.getLignes());
    }

    private Ordonnance toDomain(OrdonnanceJpaEntity e) {
        List<LigneOrdonnance> lignes = ligneJpa.findByOrdonnanceId(e.getId()).stream()
                .map(this::toLigneDomain)
                .toList();
        return toDomain(e, lignes);
    }

    private Ordonnance toDomain(OrdonnanceJpaEntity e, List<LigneOrdonnance> lignes) {
        return Ordonnance.reconstituer(e.getId(), e.getPatientId(), e.getCenterId(), e.getMedecinId(),
                e.getDatePrescription(), StatutOrdonnance.valueOf(e.getStatut()), e.getNumero(), lignes,
                e.getCreatedAt(), e.getUpdatedAt(), e.getSignedAt());
    }

    private LigneOrdonnance toLigneDomain(LigneOrdonnanceJpaEntity e) {
        ConceptCode medicament = e.getMedicamentCode() == null ? null
                : ConceptCode.of(CodingSystem.valueOf(e.getMedicamentCodeSystem()), e.getMedicamentCode(),
                e.getMedicamentCodeDisplay());
        return LigneOrdonnance.reconstituer(e.getId(), medicament, e.getLibelle(), e.getPosologie(), e.getVoie(),
                e.getDureeJours(), e.getQuantite(), e.getInstructions());
    }

    private OrdonnanceJpaEntity toJpa(Ordonnance o) {
        OrdonnanceJpaEntity e = new OrdonnanceJpaEntity();
        e.setId(o.getId());
        e.setPatientId(o.getPatientId());
        e.setCenterId(o.getCenterId());
        e.setMedecinId(o.getMedecinId());
        e.setDatePrescription(o.getDatePrescription());
        e.setStatut(o.getStatut().name());
        e.setNumero(o.getNumero());
        e.setCreatedAt(o.getCreatedAt());
        e.setUpdatedAt(o.getUpdatedAt());
        e.setSignedAt(o.getSignedAt());
        return e;
    }

    private LigneOrdonnanceJpaEntity toLigneJpa(UUID ordonnanceId, LigneOrdonnance ligne) {
        LigneOrdonnanceJpaEntity e = new LigneOrdonnanceJpaEntity();
        e.setId(ligne.getId());
        e.setOrdonnanceId(ordonnanceId);
        ConceptCode medicament = ligne.getMedicament();
        e.setMedicamentCodeSystem(medicament == null ? null : medicament.system().name());
        e.setMedicamentCode(medicament == null ? null : medicament.code());
        e.setMedicamentCodeDisplay(medicament == null ? null : medicament.display());
        e.setLibelle(ligne.getLibelle());
        e.setPosologie(ligne.getPosologie());
        e.setVoie(ligne.getVoie());
        e.setDureeJours(ligne.getDureeJours());
        e.setQuantite(ligne.getQuantite());
        e.setInstructions(ligne.getInstructions());
        return e;
    }
}
