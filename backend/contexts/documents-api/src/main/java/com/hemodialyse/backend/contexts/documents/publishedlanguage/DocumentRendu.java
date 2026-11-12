package com.hemodialyse.backend.contexts.documents.publishedlanguage;

/**
 * Un document rendu (documents.v1) : ses octets, son type MIME et le nom de fichier proposé (ex. {@code liste-patients.xlsx}). Ne pas
 * modifier le tableau d'octets.
 */
public record DocumentRendu(byte[] octets, String typeMime, String nomDeFichier) {
}
