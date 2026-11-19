package com.hemodialyse.backend.contexts.stock.interfaces.http.pages;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.consultation.pmp.ConsulterLExplicationDuPmp;
import com.hemodialyse.backend.contexts.stock.application.lectures.ConsulterLeStock.EtapeExpliquee;
import com.hemodialyse.backend.contexts.stock.application.lectures.ConsulterLeStock.ExplicationDuPmp;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Explication du PMP paginée : {@code GET /api/v1/stock/dashboard/pmp-explain/{articleId}/page} (RG-STK-093, contrat
 * {@code pagination-serveur} E5). Pagination en mémoire (rejeu calculé), aucun tri ni filtre ; le résumé est renvoyé à côté de
 * l'enveloppe {@code items, total, page, size}. Mêmes rôles que {@code GET /pmp-explain/{articleId}} (inchangée).
 */
@RestController
@RequestMapping("/api/v1/stock/dashboard")
public class ExplicationDuPmpPageControleur {

    private final SessionCourante session;
    private final ConsulterLExplicationDuPmp explication;

    public ExplicationDuPmpPageControleur(SessionCourante session, ConsulterLExplicationDuPmp explication) {
        this.session = session;
        this.explication = explication;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping("/pmp-explain/{articleId}/page")
    public PageDExplication page(@PathVariable UUID articleId, @RequestParam(required = false) UUID centerId,
                                 @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
                                 @RequestParam(required = false) String sort) {
        return PageDExplication.depuis(explication.page(session.centre(centerId), articleId, page, size, sort));
    }

    /** Étape du rejeu, même forme que dans {@code GET /pmp-explain/{articleId}}. */
    public record Etape(OffsetDateTime date, String piece, String datePiece, String type, BigDecimal quantite,
                        BigDecimal prixUnitaire, BigDecimal quantiteAvant, BigDecimal valeurAvant, BigDecimal pmpAvant,
                        BigDecimal quantiteApres, BigDecimal valeurApres, BigDecimal pmpApres, String formule) {
        static Etape depuis(EtapeExpliquee e) {
            return new Etape(e.date(), e.piece(), e.datePiece(), e.type(), e.quantite(), e.prixUnitaire(), e.quantiteAvant(),
                    e.valeurAvant(), e.pmpAvant(), e.quantiteApres(), e.valeurApres(), e.pmpApres(), e.formule());
        }
    }

    public record PageDExplication(UUID articleId, String code, String libelle, String methode, BigDecimal quantiteFinale,
                                   BigDecimal valeurFinale, BigDecimal pmpFinal, List<Etape> items, long total, int page,
                                   int size) {
        static PageDExplication depuis(ConsulterLExplicationDuPmp.PageDExplication p) {
            ExplicationDuPmp e = p.explication();
            return new PageDExplication(e.articleId(), e.code(), e.libelle(), e.methode(), e.quantiteFinale(), e.valeurFinale(),
                    e.pmpFinal(), p.etapes().items().stream().map(Etape::depuis).toList(), p.etapes().total(), p.etapes().page(),
                    p.etapes().size());
        }
    }
}
