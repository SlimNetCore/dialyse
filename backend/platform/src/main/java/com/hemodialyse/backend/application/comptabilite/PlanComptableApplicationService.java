package com.hemodialyse.backend.application.comptabilite;

import com.hemodialyse.backend.domain.comptabilite.port.ComptePayeurRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.CompteRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.EcritureComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.MappingComptablePort;
import com.hemodialyse.backend.domain.comptabilite.port.ModelePieceRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.PlanComptableUseCase;
import com.hemodialyse.backend.domain.comptabilite.service.PlanComptableService;
import com.hemodialyse.backend.domain.comptabilite.valueobject.CompteComptable;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

/**
 * Plan comptable d'un centre — enveloppe transactionnelle du domaine pur.
 */
@Service
@Transactional
public class PlanComptableApplicationService implements PlanComptableUseCase {

    private final PlanComptableService delegate;

    public PlanComptableApplicationService(CompteRepositoryPort comptes, MappingComptablePort mappings,
                                           ComptePayeurRepositoryPort comptesPayeurs,
                                           ModelePieceRepositoryPort modeles,
                                           EcritureComptableRepositoryPort ecritures) {
        this.delegate = new PlanComptableService(comptes, mappings, comptesPayeurs, modeles, ecritures);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<CompteComptable> lister(UUID centerId, String recherche, boolean actifsSeulement, int page,
                                               int size) {
        return delegate.lister(centerId, recherche, actifsSeulement, page, size);
    }

    @Override
    public CompteComptable enregistrer(UUID centerId, CompteComptable compte) {
        return delegate.enregistrer(centerId, compte);
    }

    @Override
    public void supprimer(UUID centerId, String numero) {
        delegate.supprimer(centerId, numero);
    }

    @Override
    @Transactional(readOnly = true)
    public void exigerActifs(UUID centerId, Collection<String> numeros) {
        delegate.exigerActifs(centerId, numeros);
    }
}
