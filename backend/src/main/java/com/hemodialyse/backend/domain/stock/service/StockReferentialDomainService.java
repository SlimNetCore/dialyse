package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Emplacement;
import com.hemodialyse.backend.domain.stock.model.Fournisseur;
import com.hemodialyse.backend.domain.stock.port.EmplacementRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.FournisseurRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockReferentialUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class StockReferentialDomainService implements StockReferentialUseCase {

    private final FournisseurRepositoryPort fournisseurRepo;
    private final EmplacementRepositoryPort emplacementRepo;
    private final ArticleRepositoryPort articleRepo;

    public StockReferentialDomainService(FournisseurRepositoryPort fournisseurRepo,
                                         EmplacementRepositoryPort emplacementRepo,
                                         ArticleRepositoryPort articleRepo) {
        this.fournisseurRepo = fournisseurRepo;
        this.emplacementRepo = emplacementRepo;
        this.articleRepo = articleRepo;
    }

    @Override
    public Article createArticle(CenterId centerId, String code, String libelle, String unite,
                                 BigDecimal seuilAlerte, boolean gereParLot) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code article est obligatoire");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libelle article est obligatoire");
        }
        if (unite == null || unite.isBlank()) {
            throw new IllegalArgumentException("L'unite est obligatoire");
        }

        Article article = new Article();
        article.setId(UUID.randomUUID());
        article.setCenterId(centerId.value());
        article.setCode(code.trim());
        article.setLibelle(libelle.trim());
        article.setUnite(unite.trim());
        article.setStockQuantity(BigDecimal.ZERO);
        article.setPmpCourant(BigDecimal.ZERO);
        article.setSeuilAlerte(seuilAlerte != null ? seuilAlerte : BigDecimal.ZERO);
        article.setGereParLot(gereParLot);
        article.setActive(true);
        article.setCreatedAt(OffsetDateTime.now());
        return articleRepo.save(article);
    }

    @Override
    public Fournisseur createFournisseur(CenterId centerId, String code, String raisonSociale,
                                         String contact, String telephone, String email) {
        if (raisonSociale == null || raisonSociale.isBlank()) {
            throw new IllegalArgumentException("La raison sociale est obligatoire");
        }
        return fournisseurRepo.save(Fournisseur.create(centerId.value(), code, raisonSociale, contact, telephone, email));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Fournisseur> listFournisseurs(CenterId centerId) {
        return fournisseurRepo.findAllActive(centerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Fournisseur> searchFournisseurs(CenterId centerId, String query) {
        if (query == null || query.isBlank()) {
            return fournisseurRepo.findAllActive(centerId);
        }
        return fournisseurRepo.search(centerId, query.trim());
    }

    @Override
    public Emplacement createEmplacement(CenterId centerId, String code, String libelle) {
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libelle de l'emplacement est obligatoire");
        }
        return emplacementRepo.save(Emplacement.create(centerId.value(), code, libelle));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Emplacement> listEmplacements(CenterId centerId) {
        return emplacementRepo.findAllActive(centerId);
    }
}

