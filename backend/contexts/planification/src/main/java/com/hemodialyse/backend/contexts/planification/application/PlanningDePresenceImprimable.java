package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;

/**
 * Port sortant (DOC5e, ADR 0197) : les lignes du planning de présence imprimé d'une semaine. Pour chacun des sept jours à partir de
 * {@code debutDeSemaine} (le premier jour est lu comme un dimanche, le deuxième comme un lundi, etc. : c'est le dimanche que
 * l'impression passe), les infirmiers actifs au roulement de ce jour (prévus, ou absents si une absence couvre le jour) et les
 * remplaçants posés ce jour-là ; ordre jour, code du créneau, salle, situation, infirmier. Lecture seule, bornée au centre.
 */
public interface PlanningDePresenceImprimable {

    List<LigneDuPlanningDePresence> lignes(CentreId centre, LocalDate debutDeSemaine);
}
