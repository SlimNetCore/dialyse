package com.hemodialyse.backend.contexts.documents.publishedlanguage;

import java.util.Map;
import java.util.UUID;

/**
 * Port de rendu publié par Documents (documents.v1, ADR 0140 D2) : rend le document d'un type pour un centre avec le modèle actif du
 * centre et le papier en-tête, à partir des données que le contexte demandeur fournit. Appel synchrone.
 *
 * <p>Transitoire (DOC1) : le rendu est confié au moteur de la plateforme ({@link MoteurDeRendu}) ; ses refus restent ceux du moteur
 * (modèle désactivé, type inconnu, génération impossible), repris par DOC2.
 */
public interface RenduDeDocument {

    /**
     * @param centre         le centre de l'impression
     * @param typeDeDocument le code du type (insensible à la casse)
     * @param donnees        les paramètres fournis par le contexte demandeur ; l'identité du papier en-tête est toujours posée par Documents
     * @param format         {@code PDF}, {@code EXCEL} ou {@code HTML} ; {@code null} pour le format du modèle
     */
    DocumentRendu rendre(UUID centre, String typeDeDocument, Map<String, Object> donnees, String format);

    /**
     * PLN-DOC (ADR 0167), « lecture sans écriture » : le format d'impression du type pour le centre, sans rien provisionner. Un centre sans
     * modèle de ce type obtient {@code PDF} (gabarit livré) ; un modèle désactivé refuse ({@link RenduRefuse}, {@code DOCUMENT_MODEL_INACTIVE}).
     * Le format est celui du modèle tel que saisi (peut être inconnu : le demandeur décide alors de ne pas imprimer).
     */
    default String formatDImpression(UUID centre, String typeDeDocument) {
        throw new UnsupportedOperationException("Lecture sans écriture non fournie");
    }

    /**
     * PLN-DOC (ADR 0167), « lecture sans écriture » : comme {@link #rendre} mais sans créer le modèle du centre (KB-PLN-51) : un centre sans
     * modèle de ce type utilise le gabarit livré en mémoire ; un modèle désactivé refuse ({@code DOCUMENT_MODEL_INACTIVE}). L'identité du
     * centre est posée par Documents comme pour {@link #rendre}.
     */
    default DocumentRendu rendreSansProvisionner(UUID centre, String typeDeDocument, Map<String, Object> donnees, String format) {
        throw new UnsupportedOperationException("Lecture sans écriture non fournie");
    }

    /**
     * PL9 (ADR 0148) : rend le document d'un type pour une SOCIÉTÉ sans centre (documents de direction) avec le gabarit standard livré
     * (aucun modèle de centre, donc ni désactivation ni version personnalisée) et le papier en-tête de la société.
     */
    default DocumentRendu rendrePourLaSociete(UUID societe, String typeDeDocument, Map<String, Object> donnees, String format) {
        throw new UnsupportedOperationException("Rendu sans centre non fourni");
    }
}
