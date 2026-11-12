package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.domain.FormatDeSortie;

/** Le document imprimé par une route d'impression : octets, format servi et nom de fichier proposé. */
public record DocumentImprime(byte[] octets, FormatDeSortie format, String nomDeFichier) {
}
