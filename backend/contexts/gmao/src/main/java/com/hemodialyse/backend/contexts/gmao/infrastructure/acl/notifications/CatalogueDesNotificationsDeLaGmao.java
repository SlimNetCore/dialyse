package com.hemodialyse.backend.contexts.gmao.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.Audience;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

/**
 * Catalogue des notifications émises par la GMAO (RG-NOT, ADR 0156) : code de type conservé tel quel (le front en dépend), contenu
 * composé dans l'ordre historique (table de hachage, donc même JSON), rôle ciblé repris dans {@code targetRoles}.
 * <ul>
 *   <li>{@code REFORME_PROPOSEE} : equipementId, code, statut, message ; l'habilitation {@code GMAO_REFORME} ; durable ; identifiant
 *       déterministe (équipement et instant de l'événement : un événement rejoué ne crée pas de doublon).</li>
 * </ul>
 */
final class CatalogueDesNotificationsDeLaGmao {

    static final String REFORME_PROPOSEE = "REFORME_PROPOSEE";
    private static final String HABILITATION = "GMAO_REFORME";

    private CatalogueDesNotificationsDeLaGmao() {
    }

    /** UUID v3 de « type | équipement | instant de l'événement ». */
    static UUID identifiantDeLaReformeProposee(UUID equipement, Instant survenuLe) {
        return UUID.nameUUIDFromBytes((REFORME_PROPOSEE + "|" + equipement + "|" + survenuLe).getBytes(StandardCharsets.UTF_8));
    }

    static DemandeDeNotification reformeProposee(UUID centre, UUID equipement, String code, Instant survenuLe) {
        var contenu = new HashMap<String, String>();
        contenu.put("equipementId", equipement.toString());
        contenu.put("code", code);
        contenu.put("statut", "A_REFORMER");
        contenu.put("message", "Équipement " + code + " : état constaté « à réformer » à la clôture d'une intervention. "
                + "Aucune réforme n'est décidée automatiquement : consultez l'aide à la décision de maintenance.");
        contenu.put("targetRoles", HABILITATION);
        return new DemandeDeNotification(centre, REFORME_PROPOSEE, contenu, Audience.desRoles(HABILITATION),
                identifiantDeLaReformeProposee(equipement, survenuLe), true);
    }
}
