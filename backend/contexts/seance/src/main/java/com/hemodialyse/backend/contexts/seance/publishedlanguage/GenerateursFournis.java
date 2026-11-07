package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.util.Optional;
import java.util.UUID;

/** Langage publié (fourni à la Séance par la plateforme, ADR 0088) : le générateur, équipement de la GMAO. Inconnu, supprimé ou d'un autre centre : vide. */
public interface GenerateursFournis {

    Optional<Generateur> duCentre(UUID centre, UUID equipement);

    record Generateur(UUID id, String code, String fabricant, String statut) {
    }
}
