package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/** Port sortant : les volets paramédicaux (table {@code volet_paramedical}), un par séance, bornés au centre demandé. */
public interface VoletsParamedicaux {

    /**
     * Le volet de la séance, cherché PAR LA SÉANCE (KB-SEA-94, corrigé en L4 : le centre de la séance, déjà gardé par l'appelant, fait foi).
     * Un volet posé sous un autre centre est rendu avec SON centre (celui de la ligne) ; absent : vide. Le paramètre {@code centre} est le
     * centre de la séance, il ne filtre plus.
     */
    Optional<VoletParamedical> deLaSeance(CentreId centre, SeanceId seance);

    /**
     * Enregistre le volet : l'insère s'il n'existe pas, sinon remplace les dix mesures et la date de modification (jamais la date de
     * création, ni le centre). Un SECOND volet pour la même séance, sous n'importe quel centre, échoue sur l'unicité par séance (erreur
     * d'accès aux données).
     */
    void enregistrer(VoletParamedical volet);

    /** Efface le volet de la séance du centre (suppression de la séance, SE6) ; sans effet s'il n'existe pas. */
    void effacer(CentreId centre, SeanceId seance);
}
