package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.*;
import com.hemodialyse.backend.domain.stock.port.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class BonReceptionService implements BonReceptionUseCase {

    private final BonReceptionRepositoryPort repo;
    private final BonCommandeRepositoryPort bonCommandeRepo;
    private final LotRepositoryPort lotRepo;
    private final StockMovementRepositoryPort movementRepo;
    private final ArticleRepositoryPort articleRepo;
    private final StockSequencePort sequence;
    private final PmpEngine pmpEngine;

    public BonReceptionService(BonReceptionRepositoryPort repo,
                               BonCommandeRepositoryPort bonCommandeRepo,
                               LotRepositoryPort lotRepo,
                               StockMovementRepositoryPort movementRepo,
                               ArticleRepositoryPort articleRepo,
                               StockSequencePort sequence,
                               PmpEngine pmpEngine) {
        this.repo = repo;
        this.bonCommandeRepo = bonCommandeRepo;
        this.lotRepo = lotRepo;
        this.movementRepo = movementRepo;
        this.articleRepo = articleRepo;
        this.sequence = sequence;
        this.pmpEngine = pmpEngine;
    }

    @Override
    public BonReception create(CenterId centerId, UUID bonCommandeId, UUID fournisseurId,
                               LocalDate dateReception, List<LigneReception> lignes, String userId) {
        String reference = sequence.next(centerId, "SEQ_BR");
        BonReception bon = BonReception.brouillon(centerId.value(), reference, bonCommandeId,
                fournisseurId, dateReception, userId != null ? userId : "system");
        bon.remplacerLignes(lignes);
        return repo.save(bon);
    }

    @Override
    public BonReception update(CenterId centerId, UUID bonId, UUID fournisseurId,
                               LocalDate dateReception, List<LigneReception> lignes) {
        BonReception bon = get(centerId, bonId);
        if (fournisseurId != null) {
            bon.setFournisseurId(fournisseurId);
        }
        if (dateReception != null) {
            bon.setDateReception(dateReception);
        }
        bon.remplacerLignes(lignes);
        return repo.save(bon);
    }

    @Override
    public BonReception fromBonCommande(CenterId centerId, UUID bonCommandeId, String userId) {
        BonCommande bl = bonCommandeRepo.findById(bonCommandeId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Bon de commande introuvable: " + bonCommandeId));

        List<LigneReception> lignes = new ArrayList<>();
        for (LigneBonCommande l : bl.getLignes()) {
            lignes.add(new LigneReception(UUID.randomUUID(), l.articleId(), l.quantite(),
                    l.prixUnitaire(), null, null, null, null));
        }
        return create(centerId, bonCommandeId, bl.getFournisseurId(), LocalDate.now(), lignes, userId);
    }

    @Override
    public BonReception valider(CenterId centerId, UUID bonId, String userId) {
        BonReception bon = get(centerId, bonId);
        String by = userId != null ? userId : "system";

        Set<UUID> articlesTouches = new LinkedHashSet<>();

        for (LigneReception ligne : bon.getLignes()) {
            if (ligne.articleId() == null) {
                throw new IllegalArgumentException("Article manquant sur une ligne de reception");
            }
            if (ligne.quantite() == null || ligne.quantite().signum() <= 0) {
                throw new IllegalArgumentException("Quantite invalide pour l'article " + ligne.articleId());
            }
            if (ligne.numeroLot() == null || ligne.numeroLot().isBlank()) {
                throw new IllegalArgumentException("Numero de lot obligatoire pour l'article " + ligne.articleId());
            }
            if (ligne.datePeremption() == null) {
                throw new IllegalArgumentException("Date de peremption obligatoire pour le lot " + ligne.numeroLot());
            }

            articleRepo.findById(ligne.articleId(), centerId)
                    .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + ligne.articleId()));

            Lot lot = Lot.create(centerId.value(), ligne.articleId(), bon.getId(), ligne.emplacementId(),
                    ligne.numeroLot(), ligne.datePeremption(), ligne.quantite(), ligne.prixUnitaire());
            lotRepo.save(lot);

            StockMovement entree = StockMovement.entree(centerId.value(), ligne.articleId(), lot.getId(),
                    ligne.quantite(), ligne.prixUnitaire(), by);
            movementRepo.save(entree);

            articlesTouches.add(ligne.articleId());
        }

        // PMP cascade recalculation per touched article (composite index ordering)
        for (UUID articleId : articlesTouches) {
            pmpEngine.recalculerArticle(centerId, articleId);
        }

        bon.valider();
        BonReception saved = repo.save(bon);

        if (bon.getBonCommandeId() != null) {
            bonCommandeRepo.findById(bon.getBonCommandeId(), centerId).ifPresent(bl -> {
                bl.marquerRecu();
                bonCommandeRepo.save(bl);
            });
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public BonReception get(CenterId centerId, UUID bonId) {
        return repo.findById(bonId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Bon de reception introuvable: " + bonId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BonReception> list(CenterId centerId) {
        return repo.findAll(centerId);
    }
}




