package com.hemodialyse.backend.contexts.gmao.application.intervenant;

import com.hemodialyse.backend.contexts.gmao.domain.intervenant.Intervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Vue de lecture d'un intervenant (jamais l'agrégat). Le tarif garde l'échelle de sa source : celle reçue pour la réponse
 * d'un enregistrement ou d'une correction (KB-GMAO-26), celle de la base (deux décimales) pour les lectures.
 */
public record IntervenantVue(UUID id, UUID centreId, String nom, NatureIntervenant nature, String telephone, String email,
                             BigDecimal tarifHoraire, boolean actif) {

    public static IntervenantVue depuis(Intervenant intervenant) {
        return new IntervenantVue(intervenant.id().valeur(), intervenant.centreId().valeur(), intervenant.nom(),
                intervenant.nature(), intervenant.telephone(), intervenant.email(),
                intervenant.tarif() == null ? null : intervenant.tarif().valeur(), intervenant.estActif());
    }
}
