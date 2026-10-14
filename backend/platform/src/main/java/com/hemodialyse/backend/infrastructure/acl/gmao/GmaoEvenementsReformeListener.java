package com.hemodialyse.backend.infrastructure.acl.gmao;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CauseDIndisponibilite;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementMisHorsService;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Proposition de réforme (P2, KB-GMAO-57, fonctionnalité nouvelle demandée le 2026-10-12) : quand la clôture d'une
 * intervention constate l'état {@code A_REFORMER} d'un équipement, les détenteurs de l'habilitation {@code GMAO_REFORME} du
 * centre de l'équipement en sont notifiés. Rien n'est réformé ici (RG-GMA-063) : la décision reste humaine.
 * <p>
 * Le module GMAO ne connaît pas les notifications : il publie {@link EquipementMisHorsService} (déjà publié pour l'alerte
 * Planification), cet écouteur de la plateforme le traduit. Livraison au moins une fois : l'identifiant de l'alerte est
 * déterministe, un événement rejoué ne la duplique pas. Si l'enregistrement durable de l'alerte échoue,
 * {@link NotificationService#notifyReformeProposee} lève l'exception : la publication reste incomplète dans le registre et
 * sera rejouée. À valider par le métier : texte et destinataires.
 */
@Component
public class GmaoEvenementsReformeListener {

    private final NotificationService notifications;

    public GmaoEvenementsReformeListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    void surEquipementMisHorsService(EquipementMisHorsService evenement) {
        if (evenement.statut() != StatutEquipement.A_REFORMER || evenement.cause() != CauseDIndisponibilite.INTERVENTION) {
            return;
        }
        notifications.notifyReformeProposee(evenement.centreId().valeur(), evenement.equipementId().valeur(),
                evenement.code().valeur(), evenement.survenuLe());
    }
}
