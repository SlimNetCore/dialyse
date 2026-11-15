package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.CaseDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;

import java.time.LocalDate;
import java.util.List;

/**
 * Port sortant : le planning calendaire figé d'une proposition (P24, RG-PLN-101), rattaché à son exécution et purgé avec elle.
 */
public interface CalendriersDesPropositions {

    /** Enregistre (remplace) le calendrier de l'exécution. */
    void enregistrer(CentreId centre, ExecutionId execution, List<CaseDuCalendrier> cases);

    /** Les lignes (salle x créneau) d'une semaine ; vide si le calendrier n'a pas été construit. */
    List<CaseDuCalendrier> lire(CentreId centre, ExecutionId execution, LocalDate semaineDebut);

    /** Les débuts de semaine du calendrier, dans l'ordre. */
    List<LocalDate> semaines(CentreId centre, ExecutionId execution);

    /**
     * Toutes les lignes du calendrier en texte imprimable, dans l'ordre semaine, créneau, salle (DOC5c, ADR 0195) ; vide si le calendrier
     * n'a pas été construit ou si l'exécution est d'un autre centre.
     */
    List<CaseImprimableDuCalendrier> imprimables(CentreId centre, ExecutionId execution);

    /** Supprime les calendriers dont l'exécution n'existe plus. */
    void purgerOrphelins(CentreId centre);
}
