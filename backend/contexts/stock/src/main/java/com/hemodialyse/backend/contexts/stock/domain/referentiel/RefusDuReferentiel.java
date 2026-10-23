package com.hemodialyse.backend.contexts.stock.domain.referentiel;

/**
 * Règle du référentiel violée : 422 avec un code stable (les codes du legacy sont conservés : {@code ARTICLE_CODE_REQUIS},
 * {@code ARTICLE_DOSAGE_INCOMPLET}, {@code ARTICLE_CODE_EXISTANT}…, RG-STK-001 à 008).
 */
public class RefusDuReferentiel extends RuntimeException {

    private final String code;

    public RefusDuReferentiel(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

