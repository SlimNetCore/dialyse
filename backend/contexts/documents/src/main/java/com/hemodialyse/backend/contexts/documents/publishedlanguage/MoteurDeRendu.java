package com.hemodialyse.backend.contexts.documents.publishedlanguage;

import java.util.Map;
import java.util.UUID;

/**
 * Port FOURNI PAR LA PLATEFORME (transitoire, DOC1) : le moteur de rendu actuel (Jasper : compilation, version personnalisée du centre,
 * remplissage, export). Documents le consomme sans le connaître ; le moteur migre en DOC2 et ce port devient alors un adaptateur interne.
 * Il est dans le langage publié parce que la plateforme l'implémente et que le module ne dépend pas de la plateforme.
 */
public interface MoteurDeRendu {

    /**
     * Génère le document d'un modèle donné : version personnalisée active du centre si elle existe, sinon le gabarit d'origine.
     *
     * @param format {@code PDF}, {@code EXCEL}, {@code HTML} ; tout autre est servi en PDF
     */
    byte[] rendreParModele(UUID modele, UUID centre, String cheminDuGabarit, Map<String, Object> parametres, String format) throws Exception;

    /**
     * Imprime le document d'un type pour un centre (modèle garanti par provisionnement, doit être actif, identité posée par le serveur).
     *
     * @param formatDemande {@code null} pour le format du modèle
     */
    Rendu rendreParType(UUID centre, String type, Map<String, Object> donnees, String formatDemande);

    /** PL9 : comme {@link #rendreParType} pour une société sans centre : gabarit standard du catalogue, identité de la société seule. */
    default Rendu rendreParTypePourLaSociete(UUID societe, String type, Map<String, Object> donnees, String formatDemande) {
        throw new UnsupportedOperationException("Rendu sans centre non fourni");
    }

    /** Le document produit et son format ({@code PDF}, {@code EXCEL} ou {@code HTML}). */
    record Rendu(byte[] octets, String format) {
    }
}
