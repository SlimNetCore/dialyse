package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;

import java.util.Collection;
import java.util.Comparator;

/**
 * Prend, pour la transaction de l'appelant (l'application d'une proposition d'optimisation, P8, encore dans le legacy), le verrou des
 * fiches d'infirmiers dont il va écrire le roulement ou les remplacements : les écritures de la slice P3 sur ces infirmiers
 * l'attendent, et inversement. Fiches verrouillées par identifiant croissant (ordre global des verrous, ADR 0056/0057) ; une fiche
 * inconnue du centre est ignorée.
 */
public class VerrouillerLesFichesDesInfirmiers {
    private final UniteDeTravail uniteDeTravail;
    private final InfirmiersRepository infirmiers;

    public VerrouillerLesFichesDesInfirmiers(UniteDeTravail uniteDeTravail, InfirmiersRepository infirmiers) {
        this.uniteDeTravail = uniteDeTravail;
        this.infirmiers = infirmiers;
    }

    public void executer(CentreId centre, Collection<InfirmierId> fiches) {
        uniteDeTravail.executer(() -> {
            fiches.stream().distinct().sorted(Comparator.comparing(i -> i.valeur().toString()))
                    .forEach(fiche -> infirmiers.pourEcriture(centre, fiche));
            return null;
        });
    }
}
