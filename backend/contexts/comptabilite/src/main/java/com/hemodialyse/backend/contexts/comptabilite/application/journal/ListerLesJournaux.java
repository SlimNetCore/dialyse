package com.hemodialyse.backend.contexts.comptabilite.application.journal;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

/**
 * Journaux du centre (RG-CPT-009), paginés en mémoire : quelques lignes par centre ; taille de page de 1 à 100.
 */
public final class ListerLesJournaux {

    static final int TAILLE_MAX = 100;

    private final JournauxDuCentreVus journaux;
    private final UniteDeTravail uniteDeTravail;

    public ListerLesJournaux(JournauxDuCentreVus journaux, UniteDeTravail uniteDeTravail) {
        this.journaux = journaux;
        this.uniteDeTravail = uniteDeTravail;
    }

    public PageDe<JournalVue> executer(CentreId centre, int page, int size) {
        int taille = Math.max(1, Math.min(size, TAILLE_MAX));
        int numero = Math.max(0, page);
        return uniteDeTravail.executer(() -> PageDe.decoupee(
                journaux.effectifs(centre).stream().map(JournalVue::depuis).toList(), numero, taille));
    }
}
