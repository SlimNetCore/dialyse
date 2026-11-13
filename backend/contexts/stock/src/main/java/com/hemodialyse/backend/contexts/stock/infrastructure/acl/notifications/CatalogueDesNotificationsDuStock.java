package com.hemodialyse.backend.contexts.stock.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.Audience;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;

/**
 * Catalogue des notifications émises par le Stock (RG-NOT, ADR 0156) : code de type conservé tel quel (le front en dépend), contenu
 * composé dans l'ordre historique (table de hachage, donc même JSON), rôles ciblés repris dans {@code targetRoles}.
 * <ul>
 *   <li>{@code LOTS_PROCHES_PEREMPTION} : nbLots, nbPerimes, plusProche ; ADMIN, PHARMACIEN ; durable ; identifiant déterministe
 *       (une alerte par centre et par jour du centre, RG-STK-061).</li>
 * </ul>
 */
final class CatalogueDesNotificationsDuStock {

    static final String LOTS_PROCHES_PEREMPTION = "LOTS_PROCHES_PEREMPTION";

    private CatalogueDesNotificationsDuStock() {
    }

    /** UUID v3 de « type | centre | jour du centre ». */
    static UUID identifiantDesLotsProchesPeremption(UUID centre, LocalDate jour) {
        return UUID.nameUUIDFromBytes((LOTS_PROCHES_PEREMPTION + "|" + centre + "|" + jour).getBytes(StandardCharsets.UTF_8));
    }

    static DemandeDeNotification lotsProchesPeremption(UUID centre, long nbLots, long nbPerimes, LocalDate plusProche, UUID identifiant) {
        var contenu = new HashMap<String, String>();
        contenu.put("nbLots", String.valueOf(nbLots));
        contenu.put("nbPerimes", String.valueOf(nbPerimes));
        contenu.put("plusProche", plusProche == null ? "" : plusProche.toString());
        contenu.put("targetRoles", "ADMIN,PHARMACIEN");
        return new DemandeDeNotification(centre, LOTS_PROCHES_PEREMPTION, contenu, Audience.desRoles("ADMIN", "PHARMACIEN"), identifiant, true);
    }
}
