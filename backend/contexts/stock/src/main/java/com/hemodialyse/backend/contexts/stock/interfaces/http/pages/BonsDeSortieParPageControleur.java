package com.hemodialyse.backend.contexts.stock.interfaces.http.pages;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.consultation.bons.ConsulterLesBonsParPage;
import com.hemodialyse.backend.contexts.stock.application.pagination.ParametresDePage;
import com.hemodialyse.backend.contexts.stock.interfaces.http.pages.BonsPagesJson.BonDeSortieReponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Liste paginée des bons de sortie ({@code GET /api/v1/stock/bons-sortie/page}, RG-STK-089) : mêmes rôles de lecture et même
 * centre que {@code GET /bons-sortie} (inchangée). Tri {@code reference|dateSortie|lignes|createdAt} (défaut
 * {@code createdAt desc}), filtre {@code reference} (texte).
 */
@RestController
@RequestMapping("/api/v1/stock/bons-sortie")
public class BonsDeSortieParPageControleur {

    private final SessionCourante session;
    private final ConsulterLesBonsParPage bons;

    public BonsDeSortieParPageControleur(SessionCourante session, ConsulterLesBonsParPage bons) {
        this.session = session;
        this.bons = bons;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping("/page")
    public PageJson<BonDeSortieReponse> page(@RequestParam(required = false) UUID centerId,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size,
                                             @RequestParam(required = false) String sort,
                                             @RequestParam(required = false) String reference) {
        return PageJson.depuis(bons.bonsDeSortie(session.centre(centerId), new ParametresDePage(page, size, sort), reference),
                BonDeSortieReponse::depuis);
    }
}
