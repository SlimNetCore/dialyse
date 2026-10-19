package com.hemodialyse.backend.contexts.comptabilite.application.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeComptableRepository;

import java.time.Clock;
import java.time.YearMonth;

/**
 * Clôture irréversible d'un mois pour un seul centre.
 */
public final class CloturerUnePeriode {
    private final PeriodeComptableRepository periodes;
    private final UniteDeTravail uniteDeTravail;
    private final Clock horloge;

    public CloturerUnePeriode(PeriodeComptableRepository periodes, UniteDeTravail uniteDeTravail, Clock horloge) {
        this.periodes = periodes;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public void executer(CentreId centre, YearMonth mois, String auteur) {
        uniteDeTravail.executer(() -> {
            PeriodeComptable periode = periodes.duMois(centre, mois)
                    .orElseGet(() -> PeriodeComptable.ouverte(centre, mois));
            periode.cloturer(horloge.instant(), auteur);
            periodes.enregistrer(periode);
            return null;
        });
    }
}
