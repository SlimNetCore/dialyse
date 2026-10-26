package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.application.ComptesUtilisateurs.Compte;
import com.hemodialyse.backend.contexts.planification.application.RoulementsDesInfirmiers.AffectationLue;
import com.hemodialyse.backend.contexts.planification.domain.Qualification;

import java.util.List;
import java.util.UUID;

/**
 * Lecture d'une fiche d'infirmier avec son roulement et son compte (read model : jamais l'agrégat vers l'interface).
 *
 * @param compte le compte lié, {@code null} sans compte ou si le compte n'existe plus
 */
public record FicheDetaillee(UUID id, String matricule, String nom, String prenom, String telephone,
                             Qualification qualification, boolean habiliteIsolement, boolean actif,
                             List<AffectationLue> roulement, Compte compte) {
}
