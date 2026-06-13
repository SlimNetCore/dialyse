package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.*;
import com.hemodialyse.backend.domain.stock.port.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final PmpRecalculationCoordinator recalcCoordinator;

    public BonReceptionService(BonReceptionRepositoryPort repo,
                               BonCommandeRepositoryPort bonCommandeRepo,
                               LotRepositoryPort lotRepo,
                               StockMovementRepositoryPort movementRepo,
                               ArticleRepositoryPort articleRepo,
                               StockSequencePort sequence,
                               PmpEngine pmpEngine,
                               PmpRecalculationCoordinator recalcCoordinator) {
        this.repo = repo;
        this.bonCommandeRepo = bonCommandeRepo;
        this.lotRepo = lotRepo;
        this.movementRepo = movementRepo;
        this.articleRepo = articleRepo;
        this.sequence = sequence;
        this.pmpEngine = pmpEngine;
        this.recalcCoordinator = recalcCoordinator;
    }

    @Override
    public BonReception create(CenterId centerId, UUID bonCommandeId, UUID fournisseurId,
                               LocalDate dateReception, List<LigneReception> lignes, String userId) {
        assertArticlesNotLocked(centerId, lignes);
        String reference = sequence.next(centerId, "SEQ_BR");
        BonReception bon = BonReception.brouillon(centerId.value(), reference, bonCommandeId,
                fournisseurId, dateReception, userId != null ? userId : "system");
        bon.remplacerLignes(lignes);
        return repo.save(bon);
    }

    @Override
    public BonReception update(CenterId centerId, UUID bonId, UUID fournisseurId,
                               LocalDate dateReception, List<LigneReception> lignes) {
        assertArticlesNotLocked(centerId, lignes);
        BonReception bon = get(centerId, bonId);
        if (fournisseurId != null) {
            bon.setFournisseurId(fournisseurId);
        }
        if (dateReception != null) {
            bon.setDateReception(dateReception);
        }

        if (bon.getStatut() != BonStatut.BROUILLON) {
            updateValidatedReceptionHistory(centerId, bon, lignes);
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
        assertArticlesNotLocked(centerId, bon.getLignes());
        String by = userId != null ? userId : "system";

        Set<UUID> articlesTouches = new LinkedHashSet<>();

        List<LigneReception> lignes = bon.getLignes();
        for (int i = 0; i < lignes.size(); i++) {
            LigneReception ligne = lignes.get(i);
            if (ligne.articleId() == null) {
                throw new IllegalArgumentException("Article manquant sur une ligne de reception");
            }
            if (ligne.quantite() == null || ligne.quantite().signum() <= 0) {
                throw new IllegalArgumentException("Quantite invalide pour l'article " + ligne.articleId());
            }

            var article = articleRepo.findById(ligne.articleId(), centerId)
                    .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + ligne.articleId()));
            String numeroLot = normalizeNumeroLot(article.isGereParLot(), ligne.numeroLot(), bon.getReference(), i);
            LocalDate datePeremption = normalizeDatePeremption(article.isGereParLot(), ligne.datePeremption(), numeroLot);

            Lot lot = Lot.create(centerId.value(), ligne.articleId(), bon.getId(), ligne.emplacementId(),
                    numeroLot, datePeremption, ligne.quantite(), ligne.prixUnitaire());
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

    private void assertArticlesNotLocked(CenterId centerId, List<LigneReception> lignes) {
        if (lignes == null) {
            return;
        }
        for (LigneReception ligne : lignes) {
            if (ligne == null || ligne.articleId() == null) {
                continue;
            }
            if (recalcCoordinator.isLocked(centerId, ligne.articleId())) {
                throw new IllegalStateException("Recalcul en cours pour l'article " + ligne.articleId() + ". Saisie temporairement bloquee.");
            }
        }
    }

    private void updateValidatedReceptionHistory(CenterId centerId, BonReception bon, List<LigneReception> lignes) {
        List<LigneReception> requested = lignes != null ? lignes : List.of();
        List<Lot> existingLots = lotRepo.findByBonReception(bon.getId(), centerId);

        if (requested.size() != existingLots.size()) {
            throw new IllegalStateException("Pour un BR deja valide, le nombre de lignes ne peut pas changer.");
        }

        Set<UUID> touchedArticles = new LinkedHashSet<>();
        for (int i = 0; i < existingLots.size(); i++) {
            Lot lot = existingLots.get(i);
            LigneReception line = requested.get(i);
            if (line == null || line.articleId() == null || line.quantite() == null || line.prixUnitaire() == null) {
                throw new IllegalArgumentException("Ligne de reception invalide pour recalcul historique");
            }
            if (!lot.getArticleId().equals(line.articleId())) {
                throw new IllegalStateException("Pour un BR deja valide, l'article d'une ligne ne peut pas etre modifie.");
            }

            var article = articleRepo.findById(line.articleId(), centerId)
                    .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + line.articleId()));

            BigDecimal oldInitial = lot.getQuantiteInitiale() != null ? lot.getQuantiteInitiale() : BigDecimal.ZERO;
            BigDecimal oldRemaining = lot.getQuantiteRestante() != null ? lot.getQuantiteRestante() : BigDecimal.ZERO;
            BigDecimal consumed = oldInitial.subtract(oldRemaining);
            BigDecimal newInitial = line.quantite();
            BigDecimal newRemaining = newInitial.subtract(consumed);
            if (newRemaining.signum() < 0) {
                throw new IllegalStateException("Quantite saisie inferieure a la quantite deja consommee pour le lot " + lot.getNumeroLot());
            }

            lot.setNumeroLot(normalizeNumeroLot(article.isGereParLot(), line.numeroLot(), bon.getReference(), i));
            lot.setDatePeremption(normalizeDatePeremption(article.isGereParLot(), line.datePeremption(), lot.getNumeroLot()));
            lot.setQuantiteInitiale(newInitial);
            lot.setQuantiteRestante(newRemaining);
            lot.setPmp(line.prixUnitaire());
            lotRepo.save(lot);

            StockMovement entree = movementRepo.findFirstEntreeByLot(centerId, lot.getId())
                    .orElseThrow(() -> new IllegalStateException("Mouvement d'entree introuvable pour le lot " + lot.getNumeroLot()));
            entree.setQuantite(newInitial);
            entree.setPrixUnitaire(line.prixUnitaire());
            movementRepo.save(entree);

            touchedArticles.add(line.articleId());
        }

        for (UUID articleId : touchedArticles) {
            pmpEngine.recalculerArticle(centerId, articleId);
        }
    }

    private String normalizeNumeroLot(boolean gereParLot, String numeroLot, String bonReference, int index) {
        if (gereParLot) {
            if (numeroLot == null || numeroLot.isBlank()) {
                throw new IllegalArgumentException("Numero de lot obligatoire pour un article gere par lot");
            }
            return numeroLot.trim();
        }
        if (numeroLot != null && !numeroLot.isBlank()) {
            return numeroLot.trim();
        }
        return String.format("AUTO-%s-%02d", bonReference != null ? bonReference : "BR", index + 1);
    }

    private LocalDate normalizeDatePeremption(boolean gereParLot, LocalDate datePeremption, String numeroLot) {
        if (gereParLot && datePeremption == null) {
            throw new IllegalArgumentException("Date de peremption obligatoire pour le lot " + numeroLot);
        }
        return datePeremption;
    }
}




