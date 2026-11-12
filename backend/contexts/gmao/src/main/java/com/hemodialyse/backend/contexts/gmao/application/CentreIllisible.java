package com.hemodialyse.backend.contexts.gmao.application;

/** Le centre de la session est illisible : l'impression du bon est rejetée (400), sans détail technique. */
public class CentreIllisible extends RuntimeException {

    public CentreIllisible() {
        super("Centre de la session illisible");
    }
}
