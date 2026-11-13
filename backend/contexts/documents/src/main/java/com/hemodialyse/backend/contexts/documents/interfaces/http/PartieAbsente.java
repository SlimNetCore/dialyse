package com.hemodialyse.backend.contexts.documents.interfaces.http;

/** Une partie obligatoire de la requête multipart manque (le fichier) : 400 {@code BAD_REQUEST}. */
class PartieAbsente extends RuntimeException {

    PartieAbsente(String nom) {
        super("Partie absente : " + nom);
    }
}
