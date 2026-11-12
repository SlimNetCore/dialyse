package com.hemodialyse.backend.contexts.pilotagedirection.application;

import java.util.UUID;

/**
 * Port sortant : imprime un rapport mis en page (PL9). L'adapter confie le rendu au contexte Documents ; {@code centre} est le centre dont
 * le modèle de document et le papier en-tête sont utilisés (le Pilotage n'en a pas d'autre pour une société).
 */
public interface ImpressionDuRapport {

    /** @param centre un centre de la société, ou {@code null} : société sans centre, gabarit standard et papier en-tête de la société */
    Impression imprimer(UUID societe, UUID centre, PageDuRapport page);

    /** @param octets le PDF ; @param nomDeFichier le nom proposé au téléchargement */
    record Impression(byte[] octets, String nomDeFichier) {
    }
}
