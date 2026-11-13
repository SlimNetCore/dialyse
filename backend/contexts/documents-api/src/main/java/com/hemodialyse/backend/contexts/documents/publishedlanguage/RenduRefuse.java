package com.hemodialyse.backend.contexts.documents.publishedlanguage;

/**
 * Refus du rendu d'un document, avec un code stable (DOC2, ADR 0162) : type inconnu ({@code DOCUMENT_TYPE_UNKNOWN}), modèle désactivé
 * ({@code DOCUMENT_MODEL_INACTIVE}), génération impossible ({@code DOCUMENT_GENERATION_FAILED}). Répond 422 au format commun de la plateforme,
 * à l'identique de l'ancienne {@code BusinessException} du moteur.
 */
public class RenduRefuse extends RuntimeException {

    private final String code;

    public RenduRefuse(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
