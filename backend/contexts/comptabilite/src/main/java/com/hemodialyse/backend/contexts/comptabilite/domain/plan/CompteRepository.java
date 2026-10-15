package com.hemodialyse.backend.contexts.comptabilite.domain.plan;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

import java.util.Collection;
import java.util.Optional;

/**
 * Port sortant : plan comptable enregistré d'un centre. Toute lecture et toute écriture est bornée au centre.
 */
public interface CompteRepository {

    /**
     * Nombre de comptes enregistrés ; zéro tant que le centre utilise le plan de départ.
     */
    long nombre(CentreId centre);

    Optional<CompteComptable> trouver(CentreId centre, NumeroDeCompte numero);

    /**
     * Crée le compte ou met à jour celui qui porte ce numéro dans le centre.
     */
    void enregistrer(CompteComptable compte);

    default void enregistrerTous(Collection<CompteComptable> comptes) {
        comptes.forEach(this::enregistrer);
    }

    void supprimer(CentreId centre, NumeroDeCompte numero);
}
