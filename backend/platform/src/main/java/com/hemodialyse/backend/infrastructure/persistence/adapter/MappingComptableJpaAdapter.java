package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.comptabilite.port.MappingComptablePort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.MappingComptable;
import com.hemodialyse.backend.infrastructure.persistence.entity.MappingComptableJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.MappingComptableJpaRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MappingComptableJpaAdapter implements MappingComptablePort {

    private final MappingComptableJpaRepository repo;

    public MappingComptableJpaAdapter(MappingComptableJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public MappingComptable findByCenterId(UUID centerId) {
        return repo.findByCenterId(centerId)
                .map(this::toDomain)
                .orElseGet(() -> MappingComptable.defaultFor(centerId));
    }

    @Override
    public void save(MappingComptable mapping) {
        MappingComptableJpaEntity entity = repo.findByCenterId(mapping.centerId())
                .orElseGet(() -> {
                    MappingComptableJpaEntity e = new MappingComptableJpaEntity();
                    e.setId(UUID.randomUUID());
                    e.setCenterId(mapping.centerId());
                    return e;
                });
        entity.setCompteVentes(mapping.compteVentes());
        entity.setCompteClientPatient(mapping.compteClientPatient());
        entity.setCompteClientCnas(mapping.compteClientCnas());
        entity.setCompteClientCasnos(mapping.compteClientCasnos());
        entity.setCompteClientMutuelle(mapping.compteClientMutuelle());
        entity.setCompteClientAutre(mapping.compteClientAutre());
        entity.setCompteBanque(mapping.compteBanque());
        entity.setCompteCaisse(mapping.compteCaisse());
        entity.setCompteTVACollectee(mapping.compteTVACollectee());
        repo.save(entity);
    }

    private MappingComptable toDomain(MappingComptableJpaEntity e) {
        return new MappingComptable(e.getCenterId(),
                e.getCompteVentes(), e.getCompteClientPatient(),
                e.getCompteClientCnas(), e.getCompteClientCasnos(),
                e.getCompteClientMutuelle(), e.getCompteClientAutre(),
                e.getCompteBanque(), e.getCompteCaisse(),
                e.getCompteTVACollectee() != null ? e.getCompteTVACollectee() : "44571");
    }
}

