package com.hemodialyse.backend.domain.comptabilite.port;

import com.hemodialyse.backend.domain.comptabilite.valueobject.CompteComptable;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.Collection;
import java.util.UUID;

/**
 * Port entrant — plan comptable d'un centre.
 */
public interface PlanComptableUseCase {

    /**
     * Comptes du centre (le plan de départ tant qu'il n'a rien paramétré), triés par numéro.
     *
     * @param recherche       fragment de numéro ou de libellé, facultatif
     * @param actifsSeulement ne garder que les comptes qui peuvent être choisis
     */
    PagedResult<CompteComptable> lister(UUID centerId, String recherche, boolean actifsSeulement, int page, int size);

    /**
     * Crée un compte ou modifie le libellé / l'état de celui qui porte ce numéro.
     */
    CompteComptable enregistrer(UUID centerId, CompteComptable compte);

    void supprimer(UUID centerId, String numero);

    /**
     * Refuse ({@code COMPTE_INCONNU}) tout numéro qui n'est pas un compte actif du plan du centre.
     */
    void exigerActifs(UUID centerId, Collection<String> numeros);
}
