package com.hemodialyse.backend.contexts.stock.application.consultation.pmp;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.lectures.ConsulterLeStock.EtapeExpliquee;
import com.hemodialyse.backend.contexts.stock.application.lectures.ConsulterLeStock.ExplicationDuPmp;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;

import java.util.Set;
import java.util.UUID;

/**
 * Explication du PMP d'un article paginée (RG-STK-093). Pagination EN MÉMOIRE : le rejeu du PMP est calculé (chaque étape dépend
 * de la précédente), il est donc rejoué en entier puis découpé. Ordre chronologique du rejeu, sans tri ni filtre : tout
 * {@code sort} est refusé ({@code TRI_INVALIDE}). Le résumé (méthode, quantité, valeur et PMP finaux) accompagne chaque page.
 */
public final class ConsulterLExplicationDuPmp {

    private final Explications explications;

    public ConsulterLExplicationDuPmp(Explications explications) {
        this.explications = explications;
    }

    /**
     * @throws com.hemodialyse.backend.contexts.stock.application.pagination.PaginationInvalide page, taille ou tri refusés
     * @throws com.hemodialyse.backend.contexts.stock.domain.referentiel.ArticleIntrouvable article inconnu du centre
     */
    public PageDExplication page(CentreId centre, UUID articleId, Integer page, Integer taille, String tri) {
        DemandeDePage demande = DemandeDePage.lire(page, taille, tri, Set.of(), "chronologie", false);
        ExplicationDuPmp explication = explications.expliquer(centre, articleId);
        return new PageDExplication(explication, PageDeResultats.decouper(explication.etapes(), demande));
    }

    /** Rejeu complet du PMP d'un article (fourni par la consultation du stock). */
    @FunctionalInterface
    public interface Explications {
        ExplicationDuPmp expliquer(CentreId centre, UUID articleId);
    }

    /** Une page des étapes du rejeu et le résumé de l'explication. */
    public record PageDExplication(ExplicationDuPmp explication, PageDeResultats<EtapeExpliquee> etapes) {
    }
}
