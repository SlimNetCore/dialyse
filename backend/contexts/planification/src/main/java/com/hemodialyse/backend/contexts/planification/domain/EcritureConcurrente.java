package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Une autre écriture tient le paramétrage du centre (verrou de la ligne du centre non obtenu dans le délai, ou même clé créée
 * entre-temps) : 409 {@code PLANIFICATION_ECRITURE_CONCURRENTE}. Le client relit puis recommence (décision du 26/10 : plus de
 * « dernière écriture gagnante », KB-PLN-10).
 */
public class EcritureConcurrente extends RuntimeException {
    public EcritureConcurrente() {
        super("Le paramétrage de ce centre vient d'être modifié par une autre écriture : relire puis recommencer");
    }
}
