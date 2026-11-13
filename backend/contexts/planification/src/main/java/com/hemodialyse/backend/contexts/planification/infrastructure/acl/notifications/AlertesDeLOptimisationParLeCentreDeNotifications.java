package com.hemodialyse.backend.contexts.planification.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.AlertesDeLOptimisation;

import java.time.LocalDate;
import java.util.UUID;

/**
 * ACL sortante de la Planification vers {@code notifications.v1} (ADR 0155) pour son port {@link AlertesDeLOptimisation}. Les alertes des
 * infirmiers concernés par une proposition appliquée ont un identifiant déterministe (UUID v3 de leur clé) : déjà enregistrée, l'alerte n'est
 * ni renvoyée ni dupliquée. Les séances déplacées, émises dans la transaction d'application d'une proposition, partent en temps réel après
 * son commit (revue P8, m8).
 */
public class AlertesDeLOptimisationParLeCentreDeNotifications implements AlertesDeLOptimisation {

    static final String ROULEMENT_MODIFIE = "INFIRMIER_ROULEMENT_MODIFIE";
    static final String REMPLACEMENT_AFFECTE = "INFIRMIER_REMPLACEMENT_AFFECTE";

    private final EmissionDesNotificationsDeLaPlanification emission;

    public AlertesDeLOptimisationParLeCentreDeNotifications(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion,
                                                            JournalDesNotifications journal) {
        this.emission = new EmissionDesNotificationsDeLaPlanification(notifier, diffusion, journal);
    }

    @Override
    public void seancesDeplacees(UUID centre, String perimetre, int patients, int seancesTemporaires) {
        emission.emettre(CatalogueDesNotificationsDeLaPlanification.seancesDeplacees(centre, perimetre, patients, seancesTemporaires), true);
    }

    @Override
    public void propositionASignaler(UUID centre, UUID execution, String perimetre, String motif, int valeur) {
        emission.emettre(CatalogueDesNotificationsDeLaPlanification.propositionASignaler(centre, execution, perimetre, motif, valeur), false);
    }

    @Override
    public void bilanDeLaNuit(UUID centre, String issue, int propositions, int echecs) {
        emission.emettre(CatalogueDesNotificationsDeLaPlanification.bilanDeLaNuit(centre, issue, propositions, echecs), false);
    }

    @Override
    public boolean roulementModifie(UUID centre, UUID infirmier, UUID compte, UUID execution) {
        UUID id = CatalogueDesNotificationsDeLaPlanification.identifiant(ROULEMENT_MODIFIE + "|" + execution + "|" + infirmier);
        return emission.emettreUneFois(CatalogueDesNotificationsDeLaPlanification.roulementModifie(centre, infirmier, compte, execution, id));
    }

    @Override
    public boolean remplacementAffecte(UUID centre, UUID infirmier, UUID compte, LocalDate date, UUID salle, UUID creneau,
                                       UUID execution) {
        UUID id = CatalogueDesNotificationsDeLaPlanification.identifiant(
                REMPLACEMENT_AFFECTE + "|" + execution + "|" + infirmier + "|" + date + "|" + salle + "|" + creneau);
        return emission.emettreUneFois(
                CatalogueDesNotificationsDeLaPlanification.remplacementAffecte(centre, infirmier, compte, date, salle, creneau, execution, id));
    }
}
