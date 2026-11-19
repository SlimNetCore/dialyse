package com.hemodialyse.backend.contexts.facturation.application.reglement;

import com.hemodialyse.backend.contexts.facturation.application.reglement.pagination.DemandeDePage;
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

    /**
     * Saisie des règlements paginée côté serveur (RG-REG-009) : filtres de colonne, tri de la liste blanche et découpage de la
     * page. <b>En mémoire</b> sur {@link SituationsDesFactures#toutes} : la fiche patient et la chaîne des payeurs viennent des
     * langages publiés, l'état et le solde sont calculés (F13) ; aucune colonne SQL ne les porte.
     */
    public PageDeSituations paginer(CriteresDeSituation criteres, DemandeDePage demande, FiltresDeSituation filtres) {
        List<LigneDeSituation> retenues = situations.toutes(criteres).stream()
                .filter(filtres::accepte)
                .sorted(TriDesSituations.ordre(demande))
                .toList();
        int debut = (int) Math.min(demande.decalage(), retenues.size());
        int fin = Math.min(debut + demande.taille(), retenues.size());
        return new PageDeSituations(retenues.subList(debut, fin), retenues.size(), demande.page(), demande.taille());
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
