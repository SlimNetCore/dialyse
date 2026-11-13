package com.hemodialyse.backend.contexts.documents.domain;

/**
 * Un gabarit téléversé est refusé : code stable ({@code FILE_TYPE}, {@code EMPTY}, {@code TOO_LARGE}, {@code ENCODING}) et explication
 * lisible. Le code est celui que le front lit dans {@code violations[].code} (DOC3, mêmes codes qu'avant).
 */
public class GabaritRefuse extends RuntimeException {

    private final String code;

    public GabaritRefuse(String code, String detail) {
        super(detail);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public String detail() {
        return getMessage();
    }
}
