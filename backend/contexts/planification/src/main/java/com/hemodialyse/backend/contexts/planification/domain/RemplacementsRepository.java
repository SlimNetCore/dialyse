package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Port sortant : les remplacements (un dépôt par racine d'agrégat). Toujours borné au centre.
 */
public interface RemplacementsRepository {

    /**
     * Enregistre le remplacement ; la contrainte d'unicité (remplaçant, jour, créneau) refusée devient {@link EcritureConcurrente}.
     */
    void enregistrer(Remplacement remplacement);

    Optional<Remplacement> parId(CentreId centre, RemplacementId id);

    /**
     * Cet infirmier remplace-t-il déjà ce jour-là sur ce créneau (dans n'importe quelle salle) ?
     */
    boolean remplaceDeja(CentreId centre, InfirmierId infirmier, LocalDate jour, CreneauId creneau);

    void annuler(CentreId centre, RemplacementId id);
}
