package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.UUID;

/**
 * Un traitement de l'anémie prescrit (EPO en UI, fer injectable en mg) : article du catalogue du centre, dose, voie et
 * fréquence. Tout est facultatif (reproduit : une dose sans article, un article sans dose, une dose négative ou nulle sont
 * acceptés, KB-MED-30) ; seule la longueur de la voie est bornée (colonne de 10 caractères : 400 au lieu de 500).
 */
public record Traitement(UUID article, Integer dose, String voie, Integer frequenceValeur, UniteDeFrequence frequence) {

    public static final Traitement AUCUN = new Traitement(null, null, null, null, null);

    public Traitement {
        if (voie != null && voie.length() > 10) throw new SaisieInvalide("La voie d'administration est limitée à 10 caractères");
    }
}
