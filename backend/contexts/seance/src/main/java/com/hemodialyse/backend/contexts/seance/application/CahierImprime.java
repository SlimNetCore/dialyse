package com.hemodialyse.backend.contexts.seance.application;

import java.util.Objects;

/** Le cahier rendu : ses octets et son format ({@code PDF}, {@code EXCEL} ou {@code HTML}, celui du modèle de document du centre). */
public record CahierImprime(byte[] octets, Format format) {

    public enum Format { PDF, EXCEL, HTML }

    public CahierImprime {
        Objects.requireNonNull(octets);
        Objects.requireNonNull(format);
    }
}
