package com.hemodialyse.backend.contexts.stock.interfaces.http.pages;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.consultation.etat.ConsulterLEtatDuStock;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.ArticleValorise;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * État du stock paginé : {@code GET /api/v1/stock/dashboard/stock-valorise/page} (RG-STK-090, contrat
 * {@code pagination-serveur} E1). Mêmes rôles et même isolation de centre que {@code GET /stock-valorise} (inchangée) ; éléments de
 * même forme JSON.
 */
@RestController
@RequestMapping("/api/v1/stock/dashboard")
public class EtatDuStockPageControleur {

    private final SessionCourante session;
    private final ConsulterLEtatDuStock etat;

    public EtatDuStockPageControleur(SessionCourante session, ConsulterLEtatDuStock etat) {
        this.session = session;
        this.etat = etat;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping("/stock-valorise/page")
    public PageJson<Valorise> page(@RequestParam(required = false) UUID centerId,
                                   @RequestParam(required = false) Integer page,
                                   @RequestParam(required = false) Integer size,
                                   @RequestParam(required = false) String sort,
                                   @RequestParam(required = false) String code,
                                   @RequestParam(required = false) String libelle,
                                   @RequestParam(required = false) String quantite,
                                   @RequestParam(required = false) String pmp,
                                   @RequestParam(required = false) String valeur) {
        return PageJson.depuis(etat.page(session.centre(centerId), page, size, sort, code, libelle, quantite, pmp, valeur),
                Valorise::depuis);
    }

    /** Même forme que l'élément de {@code GET /stock-valorise}. */
    public record Valorise(UUID articleId, String code, String libelle, String unite, BigDecimal quantite, BigDecimal pmpCourant,
                           BigDecimal valeur) {
        static Valorise depuis(ArticleValorise a) {
            return new Valorise(a.articleId(), a.code(), a.libelle(), a.unite(), a.quantite(), a.pmpCourant(), a.valeur());
        }
    }
}
