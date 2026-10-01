package com.hemodialyse.backend.infrastructure.persistence.adapter.gmao;

import com.hemodialyse.backend.domain.gmao.model.EquipementStatutHistorique;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.port.EquipementStatutHistoriqueRepositoryPort;
import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.EquipementStatutHistoriqueEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.gmao.EquipementStatutHistoriqueJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class EquipementStatutHistoriqueRepositoryAdapter implements EquipementStatutHistoriqueRepositoryPort {

    private final EquipementStatutHistoriqueJpaRepository jpaRepository;

    public EquipementStatutHistoriqueRepositoryAdapter(EquipementStatutHistoriqueJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(EquipementStatutHistorique entree) {
        jpaRepository.save(new EquipementStatutHistoriqueEntity(
                entree.getId(),
                entree.getEquipementId(),
                entree.getCentreId(),
                entree.getStatutPrecedent() == null ? null : entree.getStatutPrecedent().name(),
                entree.getStatutNouveau().name(),
                entree.getMotif(),
                entree.getChangedAt(),
                entree.getChangedBy()
        ));
    }

    @Override
    public List<EquipementStatutHistorique> findByEquipementIdOrderByChangedAtAsc(UUID equipementId) {
        return jpaRepository.findByEquipementIdOrderByChangedAtAsc(equipementId).stream()
                .map(e -> EquipementStatutHistorique.reconstruct(
                        e.getId(),
                        e.getEquipementId(),
                        e.getCentreId(),
                        e.getStatutPrecedent() == null ? null : StatutEquipement.valueOf(e.getStatutPrecedent()),
                        StatutEquipement.valueOf(e.getStatutNouveau()),
                        e.getMotif(),
                        e.getChangedAt(),
                        e.getChangedBy()
                ))
                .toList();
    }
}
