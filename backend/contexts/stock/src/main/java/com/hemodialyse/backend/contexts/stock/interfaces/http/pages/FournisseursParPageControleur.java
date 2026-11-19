package com.hemodialyse.backend.contexts.stock.interfaces.http.pages;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.consultation.fournisseurs.ConsulterLesFournisseursParPage;
import com.hemodialyse.backend.contexts.stock.application.pagination.ParametresDePage;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Fournisseur;
import com.hemodialyse.backend.contexts.stock.interfaces.http.ReferentielJson.FournisseurReponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Liste paginée des fournisseurs actifs ({@code GET /api/v1/stock/referentiel/fournisseurs/page}, RG-STK-087) : mêmes rôles
 * de lecture et même centre que {@code GET /referentiel/fournisseurs} (inchangée). Tri {@code code|raisonSociale|contact}
 * (défaut {@code raisonSociale asc}), filtres texte {@code q} (code, raison sociale ou contact), {@code code},
 * {@code raisonSociale}, {@code contact}.
 */
@RestController
@RequestMapping("/api/v1/stock/referentiel/fournisseurs")
public class FournisseursParPageControleur {

    private final SessionCourante session;
    private final ConsulterLesFournisseursParPage fournisseurs;

    public FournisseursParPageControleur(SessionCourante session, ConsulterLesFournisseursParPage fournisseurs) {
        this.session = session;
        this.fournisseurs = fournisseurs;
    }

    private static FournisseurReponse reponse(Fournisseur f) {
        return new FournisseurReponse(f.id(), f.centre().valeur(), f.code(), f.raisonSociale(), f.contact(), f.telephone(),
                f.email(), f.actif());
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping("/page")
    public PageJson<FournisseurReponse> page(@RequestParam(required = false) UUID centerId,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size,
                                             @RequestParam(required = false) String sort,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(required = false) String code,
                                             @RequestParam(required = false) String raisonSociale,
                                             @RequestParam(required = false) String contact) {
        return PageJson.depuis(fournisseurs.page(session.centre(centerId), new ParametresDePage(page, size, sort), q, code,
                raisonSociale, contact), FournisseursParPageControleur::reponse);
    }
}
