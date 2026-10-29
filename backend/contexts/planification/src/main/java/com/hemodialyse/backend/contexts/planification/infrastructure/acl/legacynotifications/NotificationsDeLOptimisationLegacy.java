package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacynotifications;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.NotificationsDeLOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.Perimetre;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.AlertesDeLOptimisation;

import java.time.LocalDate;
import java.util.UUID;

/**
 * ACL vers le centre de notifications (legacy) : traduit les identifiants typés du contexte en identifiants du langage publié
 * {@link AlertesDeLOptimisation}, fourni par la plateforme. Retirée quand les notifications seront un contexte.
 */
public class NotificationsDeLOptimisationLegacy implements NotificationsDeLOptimisation {

    private final AlertesDeLOptimisation alertes;
    private final InfirmiersRepository infirmiers;

    public NotificationsDeLOptimisationLegacy(AlertesDeLOptimisation alertes, InfirmiersRepository infirmiers) {
        this.alertes = alertes;
        this.infirmiers = infirmiers;
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

    /**
     * Le compte lié à la fiche de l'infirmier (destinataire de l'alerte personnelle), ou nul si la fiche n'a pas de compte.
     */
    private UUID compteDe(CentreId centre, InfirmierId infirmier) {
        return infirmiers.parId(centre, infirmier).flatMap(Infirmier::compte).map(CompteId::valeur).orElse(null);
    }

    @Override
    public boolean roulementModifie(CentreId centre, InfirmierId infirmier, ExecutionId execution) {
        return alertes.roulementModifie(centre.valeur(), infirmier.valeur(), compteDe(centre, infirmier), execution.valeur());
    }

    @Override
    public boolean remplacementAffecte(CentreId centre, InfirmierId infirmier, LocalDate date, SalleId salle, CreneauId creneau,
                                       ExecutionId execution) {
        return alertes.remplacementAffecte(centre.valeur(), infirmier.valeur(), compteDe(centre, infirmier), date, salle.valeur(),
                creneau.valeur(), execution.valeur());
    }
}
