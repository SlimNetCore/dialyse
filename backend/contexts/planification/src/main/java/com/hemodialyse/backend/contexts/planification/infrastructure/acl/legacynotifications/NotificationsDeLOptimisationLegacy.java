package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacynotifications;

import com.hemodialyse.backend.contexts.planification.application.NotificationsDeLOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.Perimetre;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.AlertesDeLOptimisation;

import java.time.LocalDate;

/**
 * ACL vers le centre de notifications (legacy) : traduit les identifiants typés du contexte en identifiants du langage publié
 * {@link AlertesDeLOptimisation}, fourni par la plateforme. Retirée quand les notifications seront un contexte.
 */
public class NotificationsDeLOptimisationLegacy implements NotificationsDeLOptimisation {

    private final AlertesDeLOptimisation alertes;

    public NotificationsDeLOptimisationLegacy(AlertesDeLOptimisation alertes) {
        this.alertes = alertes;
    }

    @Override
    public void seancesDeplacees(CentreId centre, Perimetre perimetre, int patients, int seancesTemporaires) {
        alertes.seancesDeplacees(centre.valeur(), perimetre.name(), patients, seancesTemporaires);
    }

    @Override
    public void propositionASignaler(CentreId centre, ExecutionId execution, Perimetre perimetre, String motif, int valeur) {
        alertes.propositionASignaler(centre.valeur(), execution.valeur(), perimetre.name(), motif, valeur);
    }

    @Override
    public void bilanDeLaNuit(CentreId centre, String issue, int propositions, int echecs) {
        alertes.bilanDeLaNuit(centre.valeur(), issue, propositions, echecs);
    }

    @Override
    public boolean roulementModifie(CentreId centre, InfirmierId infirmier, ExecutionId execution) {
        return alertes.roulementModifie(centre.valeur(), infirmier.valeur(), execution.valeur());
    }

    @Override
    public boolean remplacementAffecte(CentreId centre, InfirmierId infirmier, LocalDate date, SalleId salle, CreneauId creneau,
                                       ExecutionId execution) {
        return alertes.remplacementAffecte(centre.valeur(), infirmier.valeur(), date, salle.valeur(), creneau.valeur(),
                execution.valeur());
    }
}
