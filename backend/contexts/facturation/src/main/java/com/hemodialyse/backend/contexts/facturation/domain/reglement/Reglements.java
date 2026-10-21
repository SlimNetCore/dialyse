package com.hemodialyse.backend.contexts.facturation.domain.reglement;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant : les règlements des factures du centre (table {@code facture_reglements}). Un règlement enregistré ne se modifie
 * ni ne se supprime.
 */
public interface Reglements {

    /**
     * La facture du centre, avec la somme signée déjà réglée, <b>verrouillée</b> jusqu'à la fin de la transaction : deux
     * règlements simultanés de la même facture sont ainsi traités l'un après l'autre (contrôle du remboursement, clé
     * d'idempotence). Vide si la facture n'existe pas dans le centre.
     */
    Optional<FactureARegler> factureARegler(CentreId centre, UUID facture);

    /**
     * Le règlement de la facture déjà enregistré sous cette clé d'idempotence.
     */
    Optional<Reglement> parCleDIdempotence(CentreId centre, UUID facture, String cle);

    void enregistrer(Reglement reglement);

    /**
     * Les règlements de la facture, du plus récent au plus ancien (date de règlement puis date de saisie).
     */
    List<Reglement> historique(CentreId centre, UUID facture);
}
