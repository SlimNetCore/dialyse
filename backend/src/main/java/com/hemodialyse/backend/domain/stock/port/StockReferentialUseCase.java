package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Emplacement;
import com.hemodialyse.backend.domain.stock.model.Fournisseur;

import java.math.BigDecimal;
import java.util.List;

/**
 * Primary port: stock referential management (fournisseurs, emplacements).
 */
public interface StockReferentialUseCase {
    Article createArticle(CenterId centerId, String code, String libelle, String unite,
                          BigDecimal seuilAlerte, boolean gereParLot);

    List<Article> listArticles(CenterId centerId);

    Fournisseur createFournisseur(CenterId centerId, String code, String raisonSociale,
                                  String contact, String telephone, String email);

    List<Fournisseur> listFournisseurs(CenterId centerId);

    List<Fournisseur> searchFournisseurs(CenterId centerId, String query);

    Emplacement createEmplacement(CenterId centerId, String code, String libelle);

    List<Emplacement> listEmplacements(CenterId centerId);
}

