package com.hemodialyse.backend.contexts.gmao.domain.intervenant;

/** R24 : la nature (interne / externe) est requise. Message identique au legacy (parité). */
public class NatureRequise extends IllegalArgumentException {
    public NatureRequise() {
        super("Type d'intervenant requis");
    }
}
