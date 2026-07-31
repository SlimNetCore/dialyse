package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Fournisseur;
import com.hemodialyse.backend.domain.stock.port.FournisseurRepositoryPort;
import com.hemodialyse.backend.infrastructure.persistence.entity.FournisseurJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.FournisseurJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class FournisseurRepositoryAdapter implements FournisseurRepositoryPort {

    private final FournisseurJpaRepository jpa;

    public FournisseurRepositoryAdapter(FournisseurJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Fournisseur save(Fournisseur f) {
        return toDomain(jpa.save(toJpa(f)));
    }

    @Override
    public Optional<Fournisseur> findById(UUID id, CenterId centerId) {
        return jpa.findByIdAndCenterId(id, centerId.value()).map(this::toDomain);
    }

    @Override
    public List<Fournisseur> findAllActive(CenterId centerId) {
        return jpa.findByCenterIdAndActifTrueOrderByRaisonSocialeAsc(centerId.value())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Fournisseur> search(CenterId centerId, String query) {
        return jpa.findByCenterIdAndActifTrueAndRaisonSocialeContainingIgnoreCaseOrderByRaisonSocialeAsc(
                        centerId.value(), query)
                .stream().map(this::toDomain).toList();
    }

    private FournisseurJpaEntity toJpa(Fournisseur f) {
        FournisseurJpaEntity e = new FournisseurJpaEntity();
        e.setId(f.id());
        e.setCenterId(f.centerId());
        e.setCode(f.code());
        e.setRaisonSociale(f.raisonSociale());
        e.setContact(f.contact());
        e.setTelephone(f.telephone());
        e.setEmail(f.email());
        e.setActif(f.actif());
        return e;
    }

    private Fournisseur toDomain(FournisseurJpaEntity e) {
        return new Fournisseur(e.getId(), e.getCenterId(), e.getCode(), e.getRaisonSociale(),
                e.getContact(), e.getTelephone(), e.getEmail(), e.isActif());
    }
}

