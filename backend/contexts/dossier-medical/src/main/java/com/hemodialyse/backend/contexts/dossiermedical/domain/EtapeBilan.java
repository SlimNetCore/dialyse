package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.time.OffsetDateTime;

/**
 * Étape du bilan pré-greffe (value object) : un examen ou une validation à effectuer.
 */
public record EtapeBilan(String code, String libelle, boolean validee,
                         OffsetDateTime valideeLe, String valideePar) {
    public EtapeBilan {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code etape requis");
    }

    public static EtapeBilan aFaire(String code, String libelle) {
        return new EtapeBilan(code, libelle, false, null, null);
    }

    public EtapeBilan valider(OffsetDateTime maintenant, String validePar) {
        if (validee) throw new IllegalArgumentException("BILAN_ETAPE_DEJA_VALIDEE");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        return new EtapeBilan(code, libelle, true, maintenant, validePar);
    }
}
