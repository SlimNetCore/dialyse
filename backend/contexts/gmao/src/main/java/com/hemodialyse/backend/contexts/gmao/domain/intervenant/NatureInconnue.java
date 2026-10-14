package com.hemodialyse.backend.contexts.gmao.domain.intervenant;

/**
 * Code de nature inconnu. Étend {@link IllegalArgumentException} : le contrat d'erreur observé est HTTP 400 BAD_REQUEST
 * (KB-GMAO-29). Le texte exposé par le legacy (nom de classe interne de l'énumération) est reproduit par l'adapter HTTP.
 */
public class NatureInconnue extends IllegalArgumentException {
    private final String code;

    public NatureInconnue(String code) {
        super("Nature d'intervenant inconnue : " + code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
