package com.hemodialyse.backend.contexts.documents.application;

import java.sql.Connection;

/**
 * Port sortant TRANSITOIRE (DOC5a, ADR 0193) : la connexion de lecture que le moteur de rendu met à disposition des gabarits qui embarquent
 * encore leur requête SQL (11 des 14 gabarits livrés, 13 des 14 avant DOC5a et PL8/PL9 : les gabarits sans requête reçoivent leurs lignes du
 * contexte demandeur et n'ouvrent aucune connexion). Le moteur ne connaît aucune table des contextes ; il ne fait qu'ouvrir, borner et
 * fermer la connexion. La connexion est bornée : lecture seule, valable le temps du travail fourni, jamais conservée.
 * Le port disparaît avec le dernier gabarit à SQL embarqué (un gabarit converti = un gabarit de moins à servir ici).
 */
public interface ConnexionDeRendu {

    /** Exécute le travail avec une connexion de lecture, puis la referme. */
    <T> T avecLaConnexion(Travail<T> travail) throws Exception;

    @FunctionalInterface
    interface Travail<T> {
        T executer(Connection connexion) throws Exception;
    }
}
