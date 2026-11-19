package com.hemodialyse.backend.contexts.stock.interfaces.http.pages;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.consultation.bons.ConsulterLesBonsParPage;
import com.hemodialyse.backend.contexts.stock.application.pagination.ParametresDePage;
import com.hemodialyse.backend.contexts.stock.interfaces.http.pages.BonsPagesJson.BonDeReceptionReponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Liste paginée des bons de réception ({@code GET /api/v1/stock/bons-reception/page}, RG-STK-089) : mêmes rôles de lecture et
 * même centre que {@code GET /bons-reception} (inchangée). Tri {@code reference|dateReception|statut|lignes|createdAt} (défaut
 * {@code createdAt desc}), filtres {@code reference} (texte) et {@code statut} ({@code BROUILLON,VALIDE}).
 */
@RestController
@RequestMapping("/api/v1/stock/bons-reception")
public class BonsDeReceptionParPageControleur {

    private final SessionCourante session;
    private final ConsulterLesBonsParPage bons;

    public BonsDeReceptionParPageControleur(SessionCourante session, ConsulterLesBonsParPage bons) {
        this.session = session;
        this.bons = bons;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping("/page")
    public PageJson<BonDeReceptionReponse> page(@RequestParam(required = false) UUID centerId,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size,
                                                @RequestParam(required = false) String sort,
                                                @RequestParam(required = false) String reference,
                                                @RequestParam(required = false) String statut) {
        return PageJson.depuis(bons.bonsDeReception(session.centre(centerId), new ParametresDePage(page, size, sort), reference,
                statut), BonDeReceptionReponse::depuis);
    }
}
