package com.hemodialyse.backend.contexts.seance.domain;

/**
 * Le cahier de dialyse ne peut pas être imprimé : 422 avec un code stable ({@value #PERIODE_INVALIDE} ou {@value #VIDE}, codes de
 * l'ancienne plateforme reproduits).
 */
public class CahierRefuse extends RuntimeException {

    public static final String PERIODE_INVALIDE = "CAHIER_PERIODE_INVALIDE";
    public static final String VIDE = "CAHIER_VIDE";

    private final String code;

    private CahierRefuse(String code, String message) {
        super(message);
        this.code = code;
    }

    public static CahierRefuse periodeInvalide() {
        return new CahierRefuse(PERIODE_INVALIDE, "Période invalide : le début doit précéder la fin");
    }

    public static CahierRefuse vide() {
        return new CahierRefuse(VIDE, "Aucune séance validée à imprimer pour ce patient");
    }

    public String code() {
        return code;
    }
}
