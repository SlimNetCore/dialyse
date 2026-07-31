package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonCommande;
import com.hemodialyse.backend.domain.stock.model.LigneBonCommande;
import com.hemodialyse.backend.domain.stock.port.BonCommandeRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.BonCommandeUseCase;
import com.hemodialyse.backend.domain.stock.port.StockSequencePort;

import java.util.List;
import java.util.UUID;

/**
 * Domain Service — BonCommande business rules.
 * <p>
 * Pure domain class (no Spring/JPA dependency — hexagonal architecture, AGENTS.md §3).
 * Wired as a bean in {@code infrastructure/config/DomainServiceConfig}; write atomicity
 * is guaranteed by the transactional persistence adapters.
 */
public class BonCommandeService implements BonCommandeUseCase {

    private final BonCommandeRepositoryPort repo;
    private final StockSequencePort sequence;

    public BonCommandeService(BonCommandeRepositoryPort repo, StockSequencePort sequence) {
        this.repo = repo;
        this.sequence = sequence;
    }

    @Override
    public BonCommande create(CenterId centerId, UUID fournisseurId, List<LigneBonCommande> lignes, String userId) {
        String reference = sequence.next(centerId, "SEQ_BC");
        BonCommande bon = BonCommande.brouillon(centerId.value(), reference, fournisseurId,
                userId != null ? userId : "system");
        bon.remplacerLignes(lignes);
        return repo.save(bon);
    }

    @Override
    public BonCommande updateLignes(CenterId centerId, UUID bonId, List<LigneBonCommande> lignes) {
        BonCommande bon = get(centerId, bonId);
        bon.remplacerLignes(lignes);
        return repo.save(bon);
    }

    @Override
    public BonCommande valider(CenterId centerId, UUID bonId) {
        BonCommande bon = get(centerId, bonId);
        bon.valider();
        return repo.save(bon);
    }

    @Override
    public BonCommande get(CenterId centerId, UUID bonId) {
        return repo.findById(bonId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Bon de commande introuvable: " + bonId));
    }

    @Override
    public List<BonCommande> list(CenterId centerId) {
        return repo.findAll(centerId);
    }
}

