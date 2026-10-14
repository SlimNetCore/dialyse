package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementVue;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.IntervenantVue;

import java.time.Instant;
import java.util.List;

/**
 * Ce que le bon d'intervention dit de l'intervention : l'intervention elle-même (lignes de coût comprises), son équipement,
 * son intervenant (absent s'il est inconnu ou d'un autre centre), sa chronologie et l'instant d'édition. Le moteur
 * d'impression, partagé avec d'autres documents, met ces données en forme.
 */
public record DonneesDuBon(InterventionVue intervention, EquipementVue equipement, IntervenantVue intervenant,
                           List<EvenementVu> chronologie, Instant editeLe) {
}
