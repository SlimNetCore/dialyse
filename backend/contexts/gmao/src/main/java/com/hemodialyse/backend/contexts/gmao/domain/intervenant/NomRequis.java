package com.hemodialyse.backend.contexts.gmao.domain.intervenant;

/** R24 : le nom est requis. Message identique au legacy (parité). */
public class NomRequis extends IllegalArgumentException {
    public NomRequis() {
        super("Nom requis");
    }
}
