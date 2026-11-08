package com.hemodialyse.backend.contexts.seance.domain;

import java.util.Locale;

/** Ce que le détail du tableau de bord liste : les présences (séances réalisées) ou les absences du suivi. */
public enum TypeDeDetail {
    PRESENCE, ABSENCE;

    /** Espaces ignorés et casse indifférente ; tout autre mot : {@link SaisieInvalide} (400, reproduit). */
    public static TypeDeDetail lire(String brut) {
        String normalise = brut == null ? "" : brut.trim().toLowerCase(Locale.ROOT);
        if (normalise.equals("presence")) return PRESENCE;
        if (normalise.equals("absence")) return ABSENCE;
        throw new SaisieInvalide("kind must be 'presence' or 'absence'");
    }

    /** Valeur exposée dans la réponse ({@code kind}). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
