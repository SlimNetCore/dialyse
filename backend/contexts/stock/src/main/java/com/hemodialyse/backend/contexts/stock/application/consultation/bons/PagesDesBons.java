package com.hemodialyse.backend.contexts.stock.application.consultation.bons;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonDeCommande;
import com.hemodialyse.backend.contexts.stock.domain.commandes.StatutDuBon;
import com.hemodialyse.backend.contexts.stock.domain.reception.BonDeReception;
import com.hemodialyse.backend.contexts.stock.domain.sortie.BonDeSortie;

import java.util.Set;

/**
 * Port sortant : une page des bons d'un centre (commande, réception, sortie), filtrée, triée et paginée par l'adapter (en SQL :
 * {@code COUNT} + {@code ORDER BY … LIMIT/OFFSET}). Chaque bon de la page porte ses lignes. Le champ de tri de la demande
 * appartient déjà à la liste blanche de la liste ({@link ConsulterLesBonsParPage}).
 */
public interface PagesDesBons {

    /** @param reference texte en minuscules (« contient »), {@code null} : pas de filtre ; {@code statuts} vide : tous */
    record FiltreDesBonsDeCommande(String reference, Set<StatutDuBon> statuts) {
        public FiltreDesBonsDeCommande {
            statuts = Set.copyOf(statuts);
        }
    }

    /** @param reference texte en minuscules (« contient »), {@code null} : pas de filtre ; {@code statuts} vide : tous */
    record FiltreDesBonsDeReception(String reference, Set<BonDeReception.Statut> statuts) {
        public FiltreDesBonsDeReception {
            statuts = Set.copyOf(statuts);
        }
    }

    /** @param reference texte en minuscules (« contient »), {@code null} : pas de filtre (le bon de sortie n'a pas de statut) */
    record FiltreDesBonsDeSortie(String reference) {
    }

    PageDeResultats<BonDeCommande> bonsDeCommande(CentreId centre, FiltreDesBonsDeCommande filtre, DemandeDePage demande);

    PageDeResultats<BonDeReception> bonsDeReception(CentreId centre, FiltreDesBonsDeReception filtre, DemandeDePage demande);

    PageDeResultats<BonDeSortie> bonsDeSortie(CentreId centre, FiltreDesBonsDeSortie filtre, DemandeDePage demande);
}
