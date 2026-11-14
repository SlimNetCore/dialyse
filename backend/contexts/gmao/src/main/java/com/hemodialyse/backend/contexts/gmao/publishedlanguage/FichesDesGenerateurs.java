package com.hemodialyse.backend.contexts.gmao.publishedlanguage;

import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié du contexte GMAO (gmao.v1, LEC-PLN-GMAO, ADR 0179) : la fiche d'un générateur d'un centre, avec son fabricant (que
 * {@link GenerateursDuCentre} ne porte pas). Lue par la Séance pour afficher le générateur du patient. Bornée au centre, équipements
 * supprimés logiquement exclus, quel que soit le statut ; lecture seule, aucun cache.
 */
public interface FichesDesGenerateurs {

    /** @param statut code du statut ({@code EN_SERVICE}, {@code EN_MAINTENANCE}, {@code HORS_SERVICE}, {@code REFORME}...) */
    record FicheDuGenerateur(UUID id, String code, String fabricant, String statut) {
    }

    /** La fiche de l'équipement du centre ; vide s'il est inconnu, supprimé ou d'un autre centre. */
    Optional<FicheDuGenerateur> duCentre(UUID centre, UUID equipement);
}
