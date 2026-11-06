package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.time.LocalDate;

/**
 * Un point de l'historique des constantes du patient : les constantes d'une séance, à la date de la séance (quel que soit le
 * statut de la séance, KB-MED-13). Une séance sans volet paramédical figure avec des constantes vides.
 */
public record ConstantesDeSeance(SeanceId seance, LocalDate dateSeance, Constantes constantes) {
    public ConstantesDeSeance {
        if (seance == null) throw new SaisieInvalide("Séance requise");
        if (dateSeance == null) throw new SaisieInvalide("Date de séance requise");
        if (constantes == null) constantes = Constantes.vides();
    }

    /** Une séance dont le volet paramédical n'a pas été saisi. */
    public static ConstantesDeSeance sansSaisie(SeanceId seance, LocalDate dateSeance) {
        return new ConstantesDeSeance(seance, dateSeance, Constantes.vides());
    }
}
