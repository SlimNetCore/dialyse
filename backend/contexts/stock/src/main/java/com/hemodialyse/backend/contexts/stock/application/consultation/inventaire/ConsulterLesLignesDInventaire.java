package com.hemodialyse.backend.contexts.stock.application.consultation.inventaire;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.consultation.inventaire.PagesDesLignesDInventaire.EtatDesLignes;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.FiltresDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.application.pagination.PaginationInvalide;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.LigneDInventaire;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;

import java.util.Set;
import java.util.UUID;

/**
 * Feuille de comptage paginée d'un inventaire (RG-STK-092). Tri {@code article} (code), {@code lot}, {@code peremption},
 * {@code theorique}, {@code comptee}, {@code ecart}, {@code valeurEcart} (non comptées en dernier) ; défaut : ordre de la feuille
 * (position). Filtres {@code etat} (une valeur parmi {@code TOUTES}, {@code A_COMPTER}, {@code ECARTS}, {@code COMPTEES}) et
 * {@code q} (code, libellé ou n° de lot). Un inventaire inconnu du centre est refusé comme sa consultation
 * ({@code INVENTORY_NOT_FOUND}).
 */
public final class ConsulterLesLignesDInventaire {

    public static final Set<String> CHAMPS_TRIABLES = Set.of("article", "lot", "peremption", "theorique", "comptee", "ecart",
            "valeurEcart");

    private final PagesDesLignesDInventaire pages;

    public ConsulterLesLignesDInventaire(PagesDesLignesDInventaire pages) {
        this.pages = pages;
    }

    /**
     * @throws PaginationInvalide paramètres refusés (dont plusieurs valeurs d'{@code etat} : {@code FILTRE_INVALIDE})
     * @throws RefusDuReferentiel {@code INVENTORY_NOT_FOUND}
     */
    public PageDeResultats<LigneDInventaire> lignes(CentreId centre, UUID inventaire, Integer page, Integer taille, String tri,
                                                    String etat, String recherche) {
        DemandeDePage demande = DemandeDePage.lire(page, taille, tri, CHAMPS_TRIABLES, "position", false);
        Set<EtatDesLignes> etats = FiltresDePage.enumeration("etat", etat, EtatDesLignes.class);
        if (etats.size() > 1) {
            throw new PaginationInvalide(PaginationInvalide.FILTRE_INVALIDE, "Le filtre « etat » n'accepte qu'une valeur : " + etat);
        }
        var filtres = new PagesDesLignesDInventaire.Filtres(etats.stream().findFirst().orElse(EtatDesLignes.TOUTES),
                FiltresDePage.texte(recherche));
        if (!pages.existe(centre, inventaire)) {
            throw new RefusDuReferentiel("INVENTORY_NOT_FOUND", "Inventaire introuvable pour ce centre.");
        }
        return pages.lignes(centre, inventaire, filtres, demande);
    }
}
