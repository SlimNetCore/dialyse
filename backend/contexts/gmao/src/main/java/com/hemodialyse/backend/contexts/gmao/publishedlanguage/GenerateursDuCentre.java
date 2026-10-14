package com.hemodialyse.backend.contexts.gmao.publishedlanguage;

import java.util.List;
import java.util.UUID;

/**
 * Langage publié du contexte GMAO : les générateurs de dialyse d'un centre, quel que soit leur statut (en service, en
 * maintenance, hors service, réformé...), supprimés logiquement exclus, triés par code. Lu par la reprise de données pour
 * retrouver le générateur d'un patient ; en lecture seule.
 */
public interface GenerateursDuCentre {

    List<Generateur> duCentre(UUID centreId);

    /** @param statut code du statut ({@code EN_SERVICE}, {@code EN_MAINTENANCE}, {@code HORS_SERVICE}, {@code REFORME}...) */
    record Generateur(UUID id, String code, UUID salleId, String statut, UUID centreId) {
    }
}
