package com.hemodialyse.backend.contexts.comptabilite.domain.mapping;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

import java.util.Optional;

/**
 * Port sortant : le mapping enregistré d'un centre (une seule ligne par centre). Toute lecture et toute écriture est bornée au centre.
 */
public interface MappingComptableRepository {

    /**
     * Le mapping enregistré ; vide tant que le centre n'a rien enregistré (le mapping par défaut s'applique alors).
     */
    Optional<MappingComptable> duCentre(CentreId centre);

    /**
     * Crée la ligne du centre ou remplace celle qui existe.
     */
    void enregistrer(MappingComptable mapping);
}
