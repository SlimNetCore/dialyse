package com.hemodialyse.backend.contexts.facturation.application.reglement;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.Reglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.Reglements;

import java.util.List;
import java.util.UUID;

/**
 * Lectures des règlements (aucune écriture) : recherche paginée des factures avec leur situation, tableau de bord, export
 * (mêmes lignes, toutes) et historique des règlements d'une facture. Le filtre porte sur la date de facturation (KB-FAC-45).
 */
public final class ConsulterLesReglements {

    private final SituationsDesFactures situations;
    private final Reglements reglements;

    public ConsulterLesReglements(SituationsDesFactures situations, Reglements reglements) {
        this.situations = situations;
        this.reglements = reglements;
    }

    /**
     * @throws IllegalArgumentException page négative ou taille nulle (la taille n'est pas plafonnée, KB-FAC-49)
     */
    public PageDeSituations rechercher(CriteresDeSituation criteres, int page, int taille) {
        if (page < 0) {
            throw new IllegalArgumentException("La page doit etre positive");
        }
        if (taille <= 0) {
            throw new IllegalArgumentException("La taille de page doit etre strictement positive");
        }
        return situations.rechercher(criteres, page, taille);
    }

    public TableauDeBord tableauDeBord(CriteresDeSituation criteres) {
        return TableauDeBord.etablir(criteres, situations.toutes(criteres));
    }

    public List<LigneDeSituation> exporter(CriteresDeSituation criteres) {
        return situations.toutes(criteres);
    }

    /**
     * Les règlements de la facture, du plus récent au plus ancien ; vide si la facture n'existe pas dans le centre.
     */
    public List<Reglement> historique(CentreId centre, UUID facture) {
        return reglements.historique(centre, facture);
    }
}
