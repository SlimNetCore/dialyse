package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Port sortant : la validation de la séance par l'infirmier (slice SE2, encore LEGACY : changement de statut, mémorisation de la place,
 * signature). SE1 ne la réécrit pas : le scan crée PUIS valide par ce port, dans la même unité de travail. Retiré quand SE2 sera reprise.
 */
public interface ValidationDeSeance {
    /** Valide la séance et rend son nouvel état ; sans effet si elle l'est déjà. */
    Seance validerParLInfirmier(CentreId centre, SeanceId seance, String auteur);
}
