package com.hemodialyse.backend.contexts.comptabilite.application.tva;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaRepository;

import java.util.List;

/**
 * Toutes les règles de TVA du centre (actives ou non, tous types), de la plus récente à la plus ancienne ; ni pagination ni filtre.
 */
public final class ListerLesReglesDeTva {

    private final RegleTvaRepository regles;
    private final UniteDeTravail uniteDeTravail;

    public ListerLesReglesDeTva(RegleTvaRepository regles, UniteDeTravail uniteDeTravail) {
        this.regles = regles;
        this.uniteDeTravail = uniteDeTravail;
    }

    public List<RegleTvaVue> executer(CentreId centre) {
        return uniteDeTravail.executer(() -> regles.duCentre(centre).stream().map(RegleTvaVue::depuis).toList());
    }
}
