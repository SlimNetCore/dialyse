package com.hemodialyse.backend.contexts.documents.application;

import java.util.Optional;

/**
 * Port sortant : les gabarits livrés avec l'application (jamais un chemin disque libre). Sert de référence au contrôle de sécurité d'un
 * dépôt et de repli quand aucune version personnalisée n'est en vigueur.
 */
public interface GabaritsLivres {

    /**
     * Le contenu du gabarit livré désigné par ce chemin de modèle (seul le nom de fichier {@code [A-Za-z0-9_-]+.jrxml} compte) ; vide si le
     * chemin est douteux, le gabarit absent ou illisible.
     */
    Optional<String> source(String cheminDuGabarit);
}
