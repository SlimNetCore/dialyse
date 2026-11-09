package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/** Port sortant : les volets paramédicaux (table {@code volet_paramedical}), un par séance, bornés au centre demandé. */
public interface VoletsParamedicaux {

    /** Le volet de la séance AU CENTRE demandé ; absent, ou posé sous un autre centre (KB-SEA-94) : vide. */
    Optional<VoletParamedical> deLaSeance(CentreId centre, SeanceId seance);

    /**
     * Enregistre le volet : l'insère s'il n'existe pas, sinon remplace les dix mesures et la date de modification (jamais la date de
     * création). Un volet de la même séance posé sous un autre centre fait échouer l'insertion sur l'unicité par séance (KB-SEA-94,
     * reproduit : erreur d'accès aux données).
     */
    void enregistrer(VoletParamedical volet);

    /** Efface le volet de la séance du centre (suppression de la séance, SE6) ; sans effet s'il n'existe pas. */
    void effacer(CentreId centre, SeanceId seance);
}
