package com.hemodialyse.backend.contexts.seance.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Un patient du centre tel que le tableau de bord le lit : son sexe et sa naissance (répartitions), son admission, son sommeil, son état
 * et la date de cet état (fin de prise en charge), ses jours de dialyse. Valeur de lecture : aucune règle d'écriture.
 *
 * @param admission peut être nulle (alors le patient est pris en charge dès le premier jour du mois)
 */
public record PatientDuMois(PatientId id, String sexe, LocalDate naissance, LocalDate admission, boolean enSommeil, String etat,
                            LocalDate dateDeLEtat, Set<DayOfWeek> joursDeDialyse) {

    public PatientDuMois {
        joursDeDialyse = Set.copyOf(joursDeDialyse);
    }

    /** Premier jour où le patient est attendu dans la période : son admission, ou le début de la période si elle est antérieure. */
    public LocalDate premierJourAttendu(LocalDate debut) {
        return admission == null || admission.isBefore(debut) ? debut : admission;
    }

    /** Dernier jour où il est attendu dans la période : borné par la libération de sa place ({@link LocalDate#MIN} : déjà libérée). */
    public LocalDate dernierJourAttendu(LocalDate fin) {
        LocalDate sortie = FinDeLaPriseEnCharge.dernierJourAttendu(etat, dateDeLEtat).orElse(fin);
        return sortie.isBefore(fin) ? sortie : fin;
    }

    public boolean dialyseLe(LocalDate jour) {
        return joursDeDialyse.contains(jour.getDayOfWeek());
    }

    /** Le patient, pour les répartitions. */
    public Demographie demographie() {
        return new Demographie(id, sexe, naissance);
    }
}
