package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.application.FicheDetaillee;
import com.hemodialyse.backend.contexts.planification.domain.Qualification;

import java.util.List;
import java.util.UUID;

/**
 * @param compte le compte lié à la fiche, absent si la fiche n'a pas d'accès à l'application
 */
public record InfirmierReponse(UUID id, String matricule, String nom, String prenom, String telephone,
                               Qualification qualification, boolean habiliteIsolement, boolean actif,
                               List<AffectationReponse> affectations, CompteReponse compte) {
    public static InfirmierReponse depuis(FicheDetaillee f) {
        return new InfirmierReponse(f.id(), f.matricule(), f.nom(), f.prenom(), f.telephone(), f.qualification(),
                f.habiliteIsolement(), f.actif(), f.roulement().stream().map(AffectationReponse::depuis).toList(),
                CompteReponse.depuis(f.compte()));
    }
}
