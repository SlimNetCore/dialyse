package com.hemodialyse.backend.contexts.stock.interfaces.http.pages;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.consultation.bons.ConsulterLesBonsParPage;
import com.hemodialyse.backend.contexts.stock.application.pagination.ParametresDePage;
import com.hemodialyse.backend.contexts.stock.interfaces.http.pages.BonsPagesJson.BonDeCommandeReponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Liste paginée des bons de commande ({@code GET /api/v1/stock/bons-commande/page}, RG-STK-088) : mêmes rôles de lecture et
 * même centre que {@code GET /bons-commande} (inchangée). Tri {@code reference|statut|lignes|createdAt} (défaut
 * {@code createdAt desc}), filtres {@code reference} (texte) et {@code statut} ({@code BROUILLON,VALIDE,RECU}).
 */
@RestController
@RequestMapping("/api/v1/stock/bons-commande")
public class BonsDeCommandeParPageControleur {

    private final SessionCourante session;
    private final ConsulterLesBonsParPage bons;

    public BonsDeCommandeParPageControleur(SessionCourante session, ConsulterLesBonsParPage bons) {
        this.session = session;
        this.bons = bons;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping("/page")
    public PageJson<BonDeCommandeReponse> page(@RequestParam(required = false) UUID centerId,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @RequestParam(required = false) String sort,
                                               @RequestParam(required = false) String reference,
                                               @RequestParam(required = false) String statut) {
        return PageJson.depuis(bons.bonsDeCommande(session.centre(centerId), new ParametresDePage(page, size, sort), reference,
                statut), BonDeCommandeReponse::depuis);
    }
}
