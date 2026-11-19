package com.hemodialyse.backend.contexts.stock.application.consultation.bons;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.consultation.bons.PagesDesBons.FiltreDesBonsDeCommande;
import com.hemodialyse.backend.contexts.stock.application.consultation.bons.PagesDesBons.FiltreDesBonsDeReception;
import com.hemodialyse.backend.contexts.stock.application.consultation.bons.PagesDesBons.FiltreDesBonsDeSortie;
import com.hemodialyse.backend.contexts.stock.application.pagination.FiltresDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.application.pagination.ParametresDePage;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonDeCommande;
import com.hemodialyse.backend.contexts.stock.domain.commandes.StatutDuBon;
import com.hemodialyse.backend.contexts.stock.domain.reception.BonDeReception;
import com.hemodialyse.backend.contexts.stock.domain.sortie.BonDeSortie;

import java.util.Set;

/**
 * Cas d'usage de lecture : listes paginées des bons de commande, de réception et de sortie d'un centre (RG-STK-087 à 089).
 * Valide la page, la taille (1 à 100), le tri (liste blanche par liste) et les filtres (codes 400 stables) puis délègue la
 * lecture au port {@link PagesDesBons}. Défaut des trois listes : création la plus récente d'abord, puis référence
 * décroissante (même ordre que les listes complètes existantes).
 */
public class ConsulterLesBonsParPage {

    public static final Set<String> TRIS_DES_BONS_DE_COMMANDE = Set.of("reference", "statut", "lignes", "createdAt");
    public static final Set<String> TRIS_DES_BONS_DE_RECEPTION = Set.of("reference", "dateReception", "statut", "lignes",
            "createdAt");
    public static final Set<String> TRIS_DES_BONS_DE_SORTIE = Set.of("reference", "dateSortie", "lignes", "createdAt");
    private static final String TRI_PAR_DEFAUT = "createdAt";

    private final PagesDesBons pages;

    public ConsulterLesBonsParPage(PagesDesBons pages) {
        this.pages = pages;
    }

    /** Filtres : {@code reference} (texte), {@code statut} (une ou plusieurs valeurs de {@link StatutDuBon}). */
    public PageDeResultats<BonDeCommande> bonsDeCommande(CentreId centre, ParametresDePage parametres, String reference,
                                                         String statut) {
        var demande = parametres.lire(TRIS_DES_BONS_DE_COMMANDE, TRI_PAR_DEFAUT, true);
        var filtre = new FiltreDesBonsDeCommande(FiltresDePage.texte(reference).orElse(null),
                FiltresDePage.enumeration("statut", statut, StatutDuBon.class));
        return pages.bonsDeCommande(centre, filtre, demande);
    }

    /** Filtres : {@code reference} (texte), {@code statut} ({@code BROUILLON}, {@code VALIDE}). */
    public PageDeResultats<BonDeReception> bonsDeReception(CentreId centre, ParametresDePage parametres, String reference,
                                                           String statut) {
        var demande = parametres.lire(TRIS_DES_BONS_DE_RECEPTION, TRI_PAR_DEFAUT, true);
        var filtre = new FiltreDesBonsDeReception(FiltresDePage.texte(reference).orElse(null),
                FiltresDePage.enumeration("statut", statut, BonDeReception.Statut.class));
        return pages.bonsDeReception(centre, filtre, demande);
    }

    /** Filtre : {@code reference} (texte) ; le bon de sortie n'a pas de statut. */
    public PageDeResultats<BonDeSortie> bonsDeSortie(CentreId centre, ParametresDePage parametres, String reference) {
        var demande = parametres.lire(TRIS_DES_BONS_DE_SORTIE, TRI_PAR_DEFAUT, true);
        return pages.bonsDeSortie(centre, new FiltreDesBonsDeSortie(FiltresDePage.texte(reference).orElse(null)), demande);
    }
}
