package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Prise en charge d'un patient vue sous l'angle du tarif : le forfait qu'elle porte (l'effectif, sinon le demandé) et ses deux
 * périodes (effective, demandée), chacune ouverte si sa fin est nulle.
 *
 * @param forfait         le forfait effectif, à défaut le demandé
 * @param forfaitEffectif vrai si ce forfait est l'effectif (les prises en charge dotées d'un forfait effectif passent avant)
 * @param creeeLe         départage les prises en charge : la plus récente d'abord
 */
public record PriseEnChargeTarifee(ForfaitApplique forfait, boolean forfaitEffectif, LocalDate debutEffectif,
                                   LocalDate finEffectif, LocalDate debutDemande, LocalDate finDemande,
                                   Instant creeeLe) {

    public PriseEnChargeTarifee {
        if (forfait == null) {
            throw new IllegalArgumentException("Le forfait de la prise en charge est obligatoire");
        }
    }

    /**
     * Vrai si l'une des deux périodes (début renseigné, début inclus, fin incluse ou ouverte) couvre cette date.
     */
    boolean couvre(LocalDate date) {
        return periodeCouvre(debutEffectif, finEffectif, date) || periodeCouvre(debutDemande, finDemande, date);
    }

    private static boolean periodeCouvre(LocalDate debut, LocalDate fin, LocalDate date) {
        return debut != null && !date.isBefore(debut) && (fin == null || !date.isAfter(fin));
    }
}
