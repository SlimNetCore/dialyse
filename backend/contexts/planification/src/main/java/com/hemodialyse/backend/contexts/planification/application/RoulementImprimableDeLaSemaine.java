package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.application.LigneDuPlanningDePresence.Situation;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port sortant (DOC5e, ADR 0197) : ce que la Planification possède du planning de présence imprimé d'une semaine, sans les libellés
 * d'Organisation (salle et créneau restent des identifiants). Pour chacun des sept jours à partir de {@code debutDeSemaine} (le premier
 * jour est lu comme un dimanche, le deuxième comme un lundi, etc. : c'est le dimanche que l'impression passe) : les infirmiers actifs au
 * roulement de ce jour (prévus, ou absents si une absence les couvre) et les remplaçants posés ce jour-là. Aucun ordre garanti. Lecture
 * seule, bornée au centre.
 */
public interface RoulementImprimableDeLaSemaine {

    /** Un infirmier (nom complet imprimé) dans une salle et un créneau, un jour, avec sa situation. */
    record LigneDuRoulement(LocalDate jour, UUID salle, UUID creneau, String infirmier, Situation situation) {
    }

    List<LigneDuRoulement> lignes(CentreId centre, LocalDate debutDeSemaine);
}
