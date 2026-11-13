package com.hemodialyse.backend.contexts.documents.publishedlanguage;

/**
 * Vérifie qu'un gabarit JRXML se compile (sans cache), pour refuser dès le téléversement un modèle que le moteur ne saura pas rendre
 * (DOC2, ADR 0162). La validation de sécurité est faite avant, par {@link JrxmlSecurityValidator}.
 */
public interface VerificationDeGabarit {

    /** @throws Exception si le gabarit ne se compile pas */
    void verifierQuIlSeCompile(String xml) throws Exception;
}
