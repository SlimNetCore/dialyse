package com.hemodialyse.backend.contexts.comptabilite.publishedlanguage;

import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié du compte client propre à un payeur (RG-CPT-019), en lecture.
 */
public interface ComptesClientsDesPayeurs {

    /**
     * Compte client du payeur, tel qu'enregistré ; vide s'il n'a pas de compte propre (le compte par défaut du centre s'applique).
     */
    Optional<String> compteDuPayeur(UUID centre, UUID payeur);
}
