package com.hemodialyse.backend.contexts.gmao.application;

/** La session n'a pas de centre : l'impression du bon est refusée (403). */
public class SessionSansCentre extends RuntimeException {

    public SessionSansCentre() {
        super("Aucun centre dans la session");
    }
}
