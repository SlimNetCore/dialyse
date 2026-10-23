package com.hemodialyse.backend.contexts.facturation.application;

/**
 * Un verrou de ligne n'a pas pu être pris dans le délai de la transaction (mois de facturation, séquence de numérotation, séance)
 * ou un interblocage a été détecté : l'acte est annulé, rien n'est écrit, il peut être relancé. 409
 * {@code FACTURATION_ECRITURE_CONCURRENTE} (ADR 0043, revue F7 constat 2).
 */
public class EcritureConcurrenteDeFacturation extends RuntimeException {

    public EcritureConcurrenteDeFacturation(Throwable cause) {
        super("Une autre operation de facturation est en cours sur les memes donnees : reessayez dans quelques instants", cause);
    }
}

