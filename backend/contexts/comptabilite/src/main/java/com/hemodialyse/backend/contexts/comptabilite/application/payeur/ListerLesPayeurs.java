package com.hemodialyse.backend.contexts.comptabilite.application.payeur;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

/**
 * Payeurs du centre avec leur compte client (RG-CPT-019) ; taille de page de 1 à 100.
 */
public final class ListerLesPayeurs {

    static final int TAILLE_MAX = 100;

    private final LectureDesPayeurs payeurs;
    private final UniteDeTravail uniteDeTravail;

    public ListerLesPayeurs(LectureDesPayeurs payeurs, UniteDeTravail uniteDeTravail) {
        this.payeurs = payeurs;
        this.uniteDeTravail = uniteDeTravail;
    }

    public PageDe<PayeurVue> executer(CentreId centre, String recherche, int page, int size) {
        int taille = Math.max(1, Math.min(size, TAILLE_MAX));
        int numero = Math.max(0, page);
        return uniteDeTravail.executer(() -> payeurs.lister(centre, recherche, numero, taille));
    }
}
