package com.hemodialyse.backend.application.comptabilite;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.aggregate.ModelePiece;
import com.hemodialyse.backend.domain.comptabilite.port.EcritureComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.JournalRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.ModelePieceRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.PeriodeComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.PiecesComptablesUseCase;
import com.hemodialyse.backend.domain.comptabilite.port.PlanComptableUseCase;
import com.hemodialyse.backend.domain.comptabilite.service.PiecesComptablesService;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Modèles de pièces et pièces saisies — enveloppe transactionnelle du domaine pur.
 */
@Service
@Transactional
public class PiecesComptablesApplicationService implements PiecesComptablesUseCase {

    private final PiecesComptablesService delegate;

    public PiecesComptablesApplicationService(ModelePieceRepositoryPort modeles,
                                              EcritureComptableRepositoryPort ecritures,
                                              PeriodeComptableRepositoryPort periodes,
                                              JournalRepositoryPort journaux, PlanComptableUseCase plan) {
        this.delegate = new PiecesComptablesService(modeles, ecritures, periodes, journaux, plan);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<ModelePiece> listerModeles(UUID centerId, boolean actifsSeulement, int page, int size) {
        return delegate.listerModeles(centerId, actifsSeulement, page, size);
    }

    @Override
    public ModelePiece enregistrerModele(ModelePiece modele) {
        return delegate.enregistrerModele(modele);
    }

    @Override
    public void supprimerModele(UUID centerId, UUID modeleId) {
        delegate.supprimerModele(centerId, modeleId);
    }

    @Override
    public EcritureComptable saisir(SaisirPieceCommand commande) {
        return delegate.saisir(commande);
    }

    @Override
    public EcritureComptable extourner(UUID centerId, UUID ecritureId, LocalDate aujourdhui) {
        return delegate.extourner(centerId, ecritureId, aujourdhui);
    }
}
