package com.hemodialyse.backend.contexts.gmao.publishedlanguage;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié du contexte GMAO (gmao.v1, LEC-PAT-GMAO, ADR 0181) : la description (code, fabricant, statut) d'équipements d'un centre,
 * lue PAR LOT. Contrairement à {@link FichesDesGenerateurs}, un équipement supprimé logiquement reste décrit (dernier code, fabricant et
 * statut connus) : la fiche et le journal des mouvements d'un patient affichent encore le générateur qui lui fut affecté. Borné au centre :
 * un équipement d'un autre centre n'est pas décrit. Lecture seule, aucun cache.
 */
public interface DescriptionsDesEquipements {

    /** @param statut code du statut ({@code EN_SERVICE}, {@code EN_MAINTENANCE}, {@code HORS_SERVICE}, {@code REFORME}...) */
    record DescriptionDeLEquipement(UUID id, String code, String fabricant, String statut) {
    }

    /**
     * Descriptions, par identifiant, des équipements du centre parmi {@code equipements} (valeurs nulles ignorées) ; un identifiant inconnu
     * ou d'un autre centre est absent du résultat. Collection vide : résultat vide, sans accès à la base.
     */
    Map<UUID, DescriptionDeLEquipement> duCentre(UUID centre, Collection<UUID> equipements);
}
