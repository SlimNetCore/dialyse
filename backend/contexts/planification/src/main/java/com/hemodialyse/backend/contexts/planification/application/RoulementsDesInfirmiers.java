package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port sortant, en lecture seule : le roulement des infirmiers, propriété de la slice P3 (table {@code infirmier_affectation},
 * encore dans le legacy). Le référentiel l'embarque dans la fiche affichée.
 */
public interface RoulementsDesInfirmiers {

    /** Une affectation au roulement, telle qu'affichée ; jours dans l'ordre de la semaine (dimanche en premier). */
    record AffectationLue(UUID id, SalleId salle, CreneauId creneau, List<JourDeLaSemaine> jours) {
    }

    /** Le roulement de chacun de ces infirmiers dans le centre, par créneau puis identifiant ; absent = aucun roulement. */
    Map<InfirmierId, List<AffectationLue>> deLaListe(CentreId centre, Collection<InfirmierId> infirmiers);
}
