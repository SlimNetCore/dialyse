package com.hemodialyse.backend.contexts.stock.application.consultation.fournisseurs;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.consultation.fournisseurs.PagesDesFournisseurs.FiltreDesFournisseurs;
import com.hemodialyse.backend.contexts.stock.application.pagination.FiltresDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.application.pagination.ParametresDePage;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Fournisseur;

import java.util.Set;

/**
 * Cas d'usage de lecture : liste paginée des fournisseurs actifs d'un centre (RG-STK-087). Tri : {@code code},
 * {@code raisonSociale} (défaut, ascendant), {@code contact} ; filtres texte {@code q} (code, raison sociale ou contact),
 * {@code code}, {@code raisonSociale}, {@code contact}.
 */
public class ConsulterLesFournisseursParPage {

    public static final Set<String> TRIS_DES_FOURNISSEURS = Set.of("code", "raisonSociale", "contact");

    private final PagesDesFournisseurs pages;

    public ConsulterLesFournisseursParPage(PagesDesFournisseurs pages) {
        this.pages = pages;
    }

    public PageDeResultats<Fournisseur> page(CentreId centre, ParametresDePage parametres, String recherche, String code,
                                             String raisonSociale, String contact) {
        var demande = parametres.lire(TRIS_DES_FOURNISSEURS, "raisonSociale", false);
        var filtre = new FiltreDesFournisseurs(FiltresDePage.texte(recherche).orElse(null),
                FiltresDePage.texte(code).orElse(null), FiltresDePage.texte(raisonSociale).orElse(null),
                FiltresDePage.texte(contact).orElse(null));
        return pages.page(centre, filtre, demande);
    }
}
