package com.hemodialyse.backend.contexts.stock.interfaces.http.pages;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.consultation.seances.ConsulterLesSortiesPourSeances;
import com.hemodialyse.backend.contexts.stock.application.consultation.seances.PagesDesSortiesPourSeances.LigneDetaillee;
import com.hemodialyse.backend.contexts.stock.application.consultation.seances.PagesDesSortiesPourSeances.ResumeDePiece;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.DemiJournee;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.LigneDePiece;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Sorties pour séances paginées (RG-STK-091, contrat {@code pagination-serveur} E2 et E3) : {@code GET /page} (pièces sans leurs
 * lignes, avec {@code nombreDeLignes}) et {@code GET /{id}/lignes/page} (lignes de la pièce avec code et libellé de l'article).
 * Mêmes rôles et même isolation de centre que {@code GET} et {@code GET /{id}} (inchangées).
 */
@RestController
@RequestMapping("/api/v1/stock/sorties-pour-seances")
public class SortiesPourSeancesPagesControleur {

    private static final String LECTURE = "hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')";

    private final SessionCourante session;
    private final ConsulterLesSortiesPourSeances sorties;

    public SortiesPourSeancesPagesControleur(SessionCourante session, ConsulterLesSortiesPourSeances sorties) {
        this.session = session;
        this.sorties = sorties;
    }

    @PreAuthorize(LECTURE)
    @GetMapping("/page")
    public PageJson<PieceResumeReponse> pieces(@RequestParam(required = false) UUID centerId,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @RequestParam(required = false) String sort,
                                               @RequestParam(required = false) String reference,
                                               @RequestParam(required = false) String statut) {
        return PageJson.depuis(sorties.pieces(session.centre(centerId), page, size, sort, reference, statut),
                PieceResumeReponse::depuis);
    }

    @PreAuthorize(LECTURE)
    @GetMapping("/{id}/lignes/page")
    public PageJson<LigneDetailleeReponse> lignes(@PathVariable UUID id, @RequestParam(required = false) UUID centerId,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size,
                                                  @RequestParam(required = false) String sort,
                                                  @RequestParam(required = false) String article) {
        return PageJson.depuis(sorties.lignes(session.centre(centerId), id, page, size, sort, article), LigneDetailleeReponse::depuis);
    }

    /** Pièce de la liste ({@code PieceReponse} sans {@code lignes}, avec {@code nombreDeLignes}). */
    public record PieceResumeReponse(UUID id, UUID centerId, String reference, LocalDate jour, String salle, DemiJournee demiJournee,
                                     String statut, OffsetDateTime enregistreeLe, String enregistreePar, OffsetDateTime valideeLe,
                                     String valideePar, int nombreDeLignes) {
        static PieceResumeReponse depuis(ResumeDePiece p) {
            return new PieceResumeReponse(p.id(), p.centre().valeur(), p.reference(), p.jour(), p.salle(), p.demiJournee(),
                    p.statut().name(), p.enregistreeLe(), p.enregistreePar(), p.valideeLe(), p.valideePar(), p.nombreDeLignes());
        }
    }

    /** Ligne de pièce ({@code LigneReponse}) avec {@code articleCode} et {@code articleLibelle}. */
    public record LigneDetailleeReponse(UUID id, UUID articleId, UUID lotId, BigDecimal quantiteSortie, BigDecimal valeurUnitaire,
                                        BigDecimal quantiteConsommee, BigDecimal ecart, String motif, String articleCode,
                                        String articleLibelle) {
        static LigneDetailleeReponse depuis(LigneDetaillee d) {
            LigneDePiece l = d.ligne();
            return new LigneDetailleeReponse(l.id(), l.articleId(), l.lotId(), l.quantiteSortie(), l.valeurUnitaire(),
                    l.quantiteConsommee(), l.ecart(), l.motif(), d.articleCode(), d.articleLibelle());
        }
    }
}
