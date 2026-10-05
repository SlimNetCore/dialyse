package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.model.ArticleFiche;
import com.hemodialyse.backend.domain.article.model.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Emplacement;
import com.hemodialyse.backend.domain.stock.model.Fournisseur;

import java.util.List;
import java.util.UUID;

/**
 * Primary port: stock referential management (fournisseurs, emplacements).
 */
public interface StockReferentialUseCase {
    Article createArticle(CenterId centerId, ArticleFiche fiche);

    /**
     * Modifie la fiche d'un article du centre (le stock, le PMP et l'état actif ne sont pas concernés).
     */
    Article updateArticle(CenterId centerId, UUID articleId, ArticleFiche fiche);

    Article getArticle(CenterId centerId, UUID articleId);

    Article setArticleActive(CenterId centerId, UUID articleId, boolean active);

    /**
     * Liste paginée et filtrée des articles du centre ({@code query} : code, libellé, DCI ou code-barres).
     */
    PagedResult<Article> searchArticles(CenterId centerId, String query, Boolean active, int page, int size);

    List<Article> listArticles(CenterId centerId);

    /**
     * Liste des articles marqués pour le traitement de l'anémie — alimente les listes déroulantes
     * EPO / fer injectable de la prescription médicale.
     */
    List<Article> listArticlesByTypeTraitementAnemie(CenterId centerId, TypeTraitementAnemie typeTraitementAnemie);

    Fournisseur createFournisseur(CenterId centerId, String code, String raisonSociale,
                                  String contact, String telephone, String email);

    List<Fournisseur> listFournisseurs(CenterId centerId);

    List<Fournisseur> searchFournisseurs(CenterId centerId, String query);

    Emplacement createEmplacement(CenterId centerId, String code, String libelle);

    List<Emplacement> listEmplacements(CenterId centerId);
}

