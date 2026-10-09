package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.comptabilite.port.JournalRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.Journal;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.infrastructure.persistence.entity.JournalComptableJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.JournalComptableJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Journaux comptables d'un centre — toute lecture et toute écriture est bornée au centre.
 */
@Component
public class JournalComptableJpaAdapter implements JournalRepositoryPort {

    private final JournalComptableJpaRepository repo;

    public JournalComptableJpaAdapter(JournalComptableJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<Journal> findByCenter(UUID centerId) {
        return repo.findByCenterIdOrderByCodeAsc(centerId).stream()
                .map(e -> new Journal(JournalCode.de(e.getCode()), e.getLibelle(), e.isActif())).toList();
    }

    @Override
    public void save(UUID centerId, Journal journal) {
        JournalComptableJpaEntity entity = repo.findByCenterIdAndCode(centerId, journal.code().valeur())
                .orElseGet(() -> {
                    JournalComptableJpaEntity e = new JournalComptableJpaEntity();
                    e.setId(UUID.randomUUID());
                    e.setCenterId(centerId);
                    e.setCode(journal.code().valeur());
                    return e;
                });
        entity.setLibelle(journal.libelle());
        entity.setActif(journal.actif());
        repo.saveAndFlush(entity);
    }

    @Override
    public void delete(UUID centerId, JournalCode code) {
        repo.findByCenterIdAndCode(centerId, code.valeur()).ifPresent(repo::delete);
    }
}
