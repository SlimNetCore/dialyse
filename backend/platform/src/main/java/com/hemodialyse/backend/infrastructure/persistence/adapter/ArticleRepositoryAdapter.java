package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.model.ConditionConservation;
import com.hemodialyse.backend.domain.article.model.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.ArticleJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.ArticleJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Articles vus par le legacy du stock (bons, inventaire, dashboard) jusqu'aux slices S5 à S8. Le référentiel (fiche, recherche,
 * unicité du code) est servi par le contexte {@code stock} depuis la slice S1.
 */
@Component
public class ArticleRepositoryAdapter implements ArticleRepositoryPort {

    private final ArticleJpaRepository jpa;

    public ArticleRepositoryAdapter(ArticleJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Article> findById(UUID articleId, CenterId centerId) {
        return jpa.findByIdAndCenterId(articleId, centerId.value()).map(this::toDomain);
    }

    @Override
    public Article save(Article article) {
        return toDomain(jpa.save(toJpa(article)));
    }

    @Override
    public List<Article> findAllByCenter(CenterId centerId) {
        return jpa.findByCenterIdOrderByCode(centerId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Article> findAllByCenterAndTypeTraitementAnemie(CenterId centerId, TypeTraitementAnemie type) {
        return jpa.findByCenterIdAndTypeTraitementAnemieOrderByCode(centerId.value(), type.name()).stream()
                .map(this::toDomain).toList();
    }

    private Article toDomain(ArticleJpaEntity e) {
        Article article = new Article();
        article.setId(e.getId());
        article.setCenterId(e.getCenterId());
        article.setCode(e.getCode());
        article.setLibelle(e.getLibelle());
        article.setUnite(e.getUnite());
        article.setStockQuantity(e.getStockQuantity());
        article.setSeuilAlerte(e.getSeuilAlerte());
        article.setPmpCourant(e.getPmpCourant());
        article.setGereParLot(e.isGereParLot());
        article.setActive(e.isActive());
        article.setTypeTraitementAnemie(
                e.getTypeTraitementAnemie() == null ? null : TypeTraitementAnemie.valueOf(e.getTypeTraitementAnemie()));
        article.setCreatedAt(e.getCreatedAt());
        article.setDci(e.getDci());
        article.setFormeGalenique(e.getFormeGalenique());
        article.setCodeBarres(e.getCodeBarres());
        article.setReferenceFabricant(e.getReferenceFabricant());
        article.setUniteAchat(e.getUniteAchat());
        article.setCoefficientAchat(e.getCoefficientAchat());
        article.setDosageParUnite(e.getDosageParUnite());
        article.setUniteDosage(e.getUniteDosage());
        article.setFournisseurId(e.getFournisseurId());
        article.setTvaTypeId(e.getTvaTypeId());
        article.setPrixAchat(e.getPrixAchat());
        article.setStockMax(e.getStockMax());
        article.setPeremptionObligatoire(e.isPeremptionObligatoire());
        article.setConditionConservation(e.getConditionConservation() == null
                ? null : ConditionConservation.valueOf(e.getConditionConservation()));
        article.setProduitDangereux(e.isProduitDangereux());
        article.setDechetDasri(e.isDechetDasri());
        article.setCompteStock(e.getCompteStock());
        article.setCompteCharge(e.getCompteCharge());
        return article;
    }

    private ArticleJpaEntity toJpa(Article a) {
        ArticleJpaEntity entity = new ArticleJpaEntity();
        entity.setId(a.getId());
        entity.setCenterId(a.getCenterId());
        entity.setCode(a.getCode());
        entity.setLibelle(a.getLibelle());
        entity.setUnite(a.getUnite());
        entity.setStockQuantity(a.getStockQuantity());
        entity.setSeuilAlerte(a.getSeuilAlerte());
        entity.setPmpCourant(a.getPmpCourant());
        entity.setGereParLot(a.isGereParLot());
        entity.setActive(a.isActive());
        entity.setTypeTraitementAnemie(a.getTypeTraitementAnemie() == null ? null : a.getTypeTraitementAnemie().name());
        entity.setCreatedAt(a.getCreatedAt());
        entity.setDci(a.getDci());
        entity.setFormeGalenique(a.getFormeGalenique());
        entity.setCodeBarres(a.getCodeBarres());
        entity.setReferenceFabricant(a.getReferenceFabricant());
        entity.setUniteAchat(a.getUniteAchat());
        entity.setCoefficientAchat(a.getCoefficientAchat());
        entity.setDosageParUnite(a.getDosageParUnite());
        entity.setUniteDosage(a.getUniteDosage());
        entity.setFournisseurId(a.getFournisseurId());
        entity.setTvaTypeId(a.getTvaTypeId());
        entity.setPrixAchat(a.getPrixAchat());
        entity.setStockMax(a.getStockMax());
        entity.setPeremptionObligatoire(a.isPeremptionObligatoire());
        entity.setConditionConservation(a.getConditionConservation() == null
                ? null : a.getConditionConservation().name());
        entity.setProduitDangereux(a.isProduitDangereux());
        entity.setDechetDasri(a.isDechetDasri());
        entity.setCompteStock(a.getCompteStock());
        entity.setCompteCharge(a.getCompteCharge());
        return entity;
    }
}
