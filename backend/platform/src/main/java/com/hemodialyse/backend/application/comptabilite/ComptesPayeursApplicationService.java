package com.hemodialyse.backend.application.comptabilite;

import com.hemodialyse.backend.domain.comptabilite.port.ComptePayeurRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.ComptesPayeursUseCase;
import com.hemodialyse.backend.domain.comptabilite.port.PayeursPort;
import com.hemodialyse.backend.domain.comptabilite.port.PlanComptableUseCase;
import com.hemodialyse.backend.domain.comptabilite.service.ComptesPayeursService;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Compte client de chaque payeur d'un centre — enveloppe transactionnelle du domaine pur.
 */
@Service
@Transactional
public class ComptesPayeursApplicationService implements ComptesPayeursUseCase {

    private final ComptesPayeursService delegate;

    public ComptesPayeursApplicationService(PayeursPort payeurs, ComptePayeurRepositoryPort comptes,
                                            PlanComptableUseCase plan) {
        this.delegate = new ComptesPayeursService(payeurs, comptes, plan);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<PayeurCompte> lister(UUID centerId, String recherche, int page, int size) {
        return delegate.lister(centerId, recherche, Math.max(0, page), Math.max(1, Math.min(size, 100)));
    }

    @Override
    public PayeurCompte definir(UUID centerId, UUID payeurId, String compte) {
        return delegate.definir(centerId, payeurId, compte);
    }
}
