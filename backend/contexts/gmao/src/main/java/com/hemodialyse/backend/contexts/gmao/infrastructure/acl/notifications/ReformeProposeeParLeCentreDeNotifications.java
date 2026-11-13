package com.hemodialyse.backend.contexts.gmao.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.gmao.domain.equipement.CauseDIndisponibilite;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementMisHorsService;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import org.springframework.modulith.events.ApplicationModuleListener;

/**
 * Proposition de réforme (P2, KB-GMAO-57) : quand la clôture d'une intervention constate l'état {@code A_REFORMER} d'un équipement, les
 * détenteurs de l'habilitation {@code GMAO_REFORME} du centre de l'équipement en sont notifiés (ACL sortante vers {@code notifications.v1},
 * ADR 0156). Rien n'est réformé ici (RG-GMA-063) : la décision reste humaine. Livraison au moins une fois : l'identifiant de l'alerte est
 * déterministe, un événement rejoué ne la duplique pas ; si l'enregistrement durable échoue, l'exception remonte : la publication reste
 * incomplète dans le registre et sera rejouée. À valider par le métier : texte et destinataires.
 */
public class ReformeProposeeParLeCentreDeNotifications {

    private final EmissionDesNotificationsDeLaGmao emission;

    public ReformeProposeeParLeCentreDeNotifications(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion) {
        this.emission = new EmissionDesNotificationsDeLaGmao(notifier, diffusion);
    }

    @ApplicationModuleListener
    public void surEquipementMisHorsService(EquipementMisHorsService evenement) {
        if (evenement.statut() != StatutEquipement.A_REFORMER || evenement.cause() != CauseDIndisponibilite.INTERVENTION) {
            return;
        }
        emission.emettre(CatalogueDesNotificationsDeLaGmao.reformeProposee(evenement.centreId().valeur(),
                evenement.equipementId().valeur(), evenement.code().valeur(), evenement.survenuLe()));
    }
}
