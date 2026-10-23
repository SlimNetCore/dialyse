package com.hemodialyse.backend.infrastructure.stock;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Lot;
import com.hemodialyse.backend.domain.stock.model.SortieRequestItem;
import com.hemodialyse.backend.domain.stock.port.BonSortieUseCase;
import com.hemodialyse.backend.domain.stock.port.LotRepositoryPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Comportement historique des consommations de séance (centre dont la sortie pour séances est désactivée) : le Stock sort
 * immédiatement les lots en FEFO, sur le bon de sortie « SEANCE » unique de la séance ({@link BonSortieUseCase}). Retiré quand tous les
 * centres auront basculé sur la sortie pour séances (slice S6b).
 */
@Component
public class ConsommationsDeSeanceLegacy implements ConsommationsDeSeance {
    private final BonSortieUseCase bons;
    private final LotRepositoryPort lots;
    private final ArticleRepositoryPort articles;

    public ConsommationsDeSeanceLegacy(BonSortieUseCase bons, LotRepositoryPort lots, ArticleRepositoryPort articles) {
        this.bons = bons;
        this.lots = lots;
        this.articles = articles;
    }

    @Override
    public void ajouter(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, UUID article, BigDecimal quantite, String auteur) {
        bons.addSeanceConsommation(CenterId.of(centre), seance, patient, dateSeance, article, quantite, auteur);
    }

    @Override
    public void fixer(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, UUID article, BigDecimal quantite, String auteur) {
        bons.setSeanceConsommation(CenterId.of(centre), seance, patient, dateSeance, article, quantite, auteur);
    }

    @Override
    public void annuler(UUID centre, UUID seance, String auteur) {
        bons.annulerSortiesSeance(CenterId.of(centre), seance, auteur);
    }

    @Override
    public void administrer(UUID centre, UUID seance, UUID patient, LocalDate jour, UUID article, BigDecimal quantite, String auteur) {
        bons.createViaFefo(CenterId.of(centre), seance, patient, "ADMINISTRATION", jour, article, quantite, auteur);
    }

    /**
     * Sélectionne les lots en FEFO pour chaque article (refus s'il en manque) puis crée un bon de sortie « SEANCE » complet.
     */
    @Override
    public void enregistrerALaValidation(UUID centre, UUID seance, UUID patient, LocalDate dateSeance, List<Ligne> lignes,
                                         String auteur) {
        CenterId centreId = CenterId.of(centre);
        List<SortieRequestItem> items = new ArrayList<>();
        for (Ligne ligne : lignes) {
            var article = articles.findById(ligne.article(), centreId)
                    .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + ligne.article()));
            BigDecimal restant = ligne.quantite();
            for (Lot lot : lots.findAvailableByArticleFefo(ligne.article(), centreId)) {
                if (restant.signum() <= 0) {
                    break;
                }
                BigDecimal disponible = lot.getQuantiteRestante() != null ? lot.getQuantiteRestante() : BigDecimal.ZERO;
                if (disponible.signum() <= 0) {
                    continue;
                }
                BigDecimal prise = restant.min(disponible);
                items.add(new SortieRequestItem(ligne.article(), lot.getId(), prise));
                restant = restant.subtract(prise);
            }
            if (restant.signum() > 0) {
                throw new IllegalStateException("Stock insuffisant pour l'article " + article.getCode() + " (manque: " + restant + " "
                        + article.getUnite() + ")");
            }
        }
        bons.create(centreId, seance, patient, "SEANCE", dateSeance, items, auteur);
    }
}