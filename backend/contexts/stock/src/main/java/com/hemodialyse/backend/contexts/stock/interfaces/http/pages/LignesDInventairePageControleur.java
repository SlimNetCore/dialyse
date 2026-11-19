package com.hemodialyse.backend.contexts.stock.interfaces.http.pages;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.consultation.inventaire.ConsulterLesLignesDInventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.LigneDInventaire;
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
 * Feuille de comptage paginée : {@code GET /api/v1/stock/inventaires/{id}/lignes/page} (RG-STK-092, contrat
 * {@code pagination-serveur} E4). Mêmes rôles et même isolation de centre que {@code GET /{id}} (inchangée) ; éléments de la forme de
 * {@code details[]}.
 */
@RestController
@RequestMapping("/api/v1/stock/inventaires")
public class LignesDInventairePageControleur {

    private final SessionCourante session;
    private final ConsulterLesLignesDInventaire lignes;

    public LignesDInventairePageControleur(SessionCourante session, ConsulterLesLignesDInventaire lignes) {
        this.session = session;
        this.lignes = lignes;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping("/{id}/lignes/page")
    public PageJson<LigneReponse> page(@PathVariable UUID id, @RequestParam(required = false) UUID centerId,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size,
                                       @RequestParam(required = false) String sort,
                                       @RequestParam(required = false) String etat,
                                       @RequestParam(required = false) String q) {
        return PageJson.depuis(lignes.lignes(session.centre(centerId), id, page, size, sort, etat, q), LigneReponse::depuis);
    }

    /** Même forme qu'un élément de {@code details[]} de {@code GET /{id}}. */
    public record LigneReponse(UUID id, UUID articleId, String articleCode, String articleLibelle, String unite, UUID lotId,
                               String numeroLot, LocalDate datePeremption, BigDecimal quantiteTheorique,
                               BigDecimal quantiteComptee, BigDecimal ecart, BigDecimal pmp, BigDecimal valeurEcart,
                               String motifEcart, String comptePar, OffsetDateTime compteLe, boolean ajoutee) {
        static LigneReponse depuis(LigneDInventaire l) {
            return new LigneReponse(l.id(), l.articleId(), l.articleCode(), l.articleLibelle(), l.unite(), l.lotId(), l.numeroLot(),
                    l.datePeremption(), l.quantiteTheorique(), l.quantiteComptee(), l.ecart(), l.pmp(), l.valeurEcart(),
                    l.motifEcart(), l.comptePar(), l.compteLe(), l.ajoutee());
        }
    }
}
