package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.model.ArticleFiche;
import com.hemodialyse.backend.domain.article.model.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.article.port.ArticleCatalogPort;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Emplacement;
import com.hemodialyse.backend.domain.stock.model.Fournisseur;
import com.hemodialyse.backend.domain.stock.port.EmplacementRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.FournisseurRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockReferentialUseCase;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — stock referential business rules (articles, fournisseurs, emplacements).
 * <p>
 * Pure domain class (no Spring/JPA dependency — hexagonal architecture, AGENTS.md §3).
 * Wired as a bean in {@code infrastructure/config/DomainServiceConfig}.
 */
public class StockReferentialDomainService implements StockReferentialUseCase {

    private final FournisseurRepositoryPort fournisseurRepo;
    private final EmplacementRepositoryPort emplacementRepo;
    private final ArticleRepositoryPort articleRepo;
    private final ArticleCatalogPort articleCatalog;

    public StockReferentialDomainService(FournisseurRepositoryPort fournisseurRepo,
                                         EmplacementRepositoryPort emplacementRepo,
                                         ArticleRepositoryPort articleRepo,
                                         ArticleCatalogPort articleCatalog) {
        this.fournisseurRepo = fournisseurRepo;
        this.emplacementRepo = emplacementRepo;
        this.articleRepo = articleRepo;
        this.articleCatalog = articleCatalog;
    }

    @Override
    public Article createArticle(CenterId centerId, ArticleFiche fiche) {
        Article article = new Article();
        article.setId(UUID.randomUUID());
        article.setCenterId(centerId.value());
        article.appliquerFiche(fiche);
        exigerCodeLibre(centerId, article.getCode(), null);
        article.setStockQuantity(BigDecimal.ZERO);
        article.setPmpCourant(BigDecimal.ZERO);
        article.setActive(true);
        article.setCreatedAt(OffsetDateTime.now());
        return articleRepo.save(article);
    }

    @Override
    public Article updateArticle(CenterId centerId, UUID articleId, ArticleFiche fiche) {
        Article article = getArticle(centerId, articleId);
        article.appliquerFiche(fiche);
        exigerCodeLibre(centerId, article.getCode(), articleId);
        return articleRepo.save(article);
    }

    @Override
    public Article getArticle(CenterId centerId, UUID articleId) {
        return articleRepo.findById(articleId, centerId)
                .orElseThrow(() -> new BusinessException("ARTICLE_INTROUVABLE", "Article introuvable"));
    }

    @Override
    public Article setArticleActive(CenterId centerId, UUID articleId, boolean active) {
        Article article = getArticle(centerId, articleId);
        article.setActive(active);
        return articleRepo.save(article);
    }

    @Override
    public PagedResult<Article> searchArticles(CenterId centerId, String query, Boolean active, int page, int size) {
        return articleCatalog.findPaged(centerId, query, active, Math.max(page, 0), Math.min(Math.max(size, 1), 200));
    }

    private void exigerCodeLibre(CenterId centerId, String code, UUID articleCourant) {
        boolean pris = articleCatalog.findByCode(centerId, code)
                .filter(autre -> !autre.getId().equals(articleCourant)).isPresent();
        if (pris) {
            throw new BusinessException("ARTICLE_CODE_EXISTANT", "Ce code article existe déjà dans le centre");
        }
    }

    @Override
    public List<Article> listArticles(CenterId centerId) {
        return articleRepo.findAllByCenter(centerId);
    }

    @Override
    public List<Article> listArticlesByTypeTraitementAnemie(CenterId centerId, TypeTraitementAnemie typeTraitementAnemie) {
        return articleRepo.findAllByCenterAndTypeTraitementAnemie(centerId, typeTraitementAnemie);
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
    public List<Fournisseur> listFournisseurs(CenterId centerId) {
        return fournisseurRepo.findAllActive(centerId);
    }

    @Override
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
    public List<Emplacement> listEmplacements(CenterId centerId) {
        return emplacementRepo.findAllActive(centerId);
    }
}

