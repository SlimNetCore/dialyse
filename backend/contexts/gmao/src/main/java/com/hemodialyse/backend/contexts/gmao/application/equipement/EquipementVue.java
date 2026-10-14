package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.equipement.Equipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.TypeEquipement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Vue de lecture d'un équipement (jamais l'agrégat). Le prix garde l'échelle de sa source : celle reçue pour la réponse
 * d'une commande (KB-GMAO-35), celle de la base (deux décimales) pour les lectures.
 */
public record EquipementVue(UUID id, UUID centreId, String code, String designation, TypeEquipement type, String fabricant,
                            String modele, String numeroSerie, Instant dateInstallation, StatutEquipement statut,
                            String localisation, String observations, Instant dateCreation, Instant dateModification,
                            UUID creePar, UUID modifiePar, UUID salleId, BigDecimal prixAcquisition) {

    public static EquipementVue depuis(Equipement equipement) {
        return new EquipementVue(equipement.id().valeur(), equipement.centreId().valeur(), equipement.code().valeur(),
                equipement.designation(), equipement.type(), equipement.fabricant(), equipement.modele(),
                equipement.numeroSerie(), equipement.dateInstallation(), equipement.statut(), equipement.localisation(),
                equipement.observations(), equipement.creeLe(), equipement.modifieLe(),
                equipement.creePar().valeur(), equipement.modifiePar() == null ? null : equipement.modifiePar().valeur(),
                equipement.salleId() == null ? null : equipement.salleId().valeur(),
                equipement.prixAcquisition() == null ? null : equipement.prixAcquisition().valeur());
    }
}
