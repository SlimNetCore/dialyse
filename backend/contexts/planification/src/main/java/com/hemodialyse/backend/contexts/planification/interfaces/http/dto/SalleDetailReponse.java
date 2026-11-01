package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.application.VueDesSalles;

import java.util.List;
import java.util.UUID;

public record SalleDetailReponse(UUID id, String code, String nom, boolean isolement, Integer capacite,
                                 int nbGenerateurs,
                                 Integer placesRestantes, boolean depassement,
                                 List<GenerateurDetailReponse> generateurs) {
    public static SalleDetailReponse depuis(VueDesSalles.SalleVue s) {
        return new SalleDetailReponse(s.id(), s.code(), s.nom(), s.isolement(), s.capacite(), s.nombreDeGenerateurs(),
                s.placesRestantes(), s.depassement(), s.generateurs().stream()
                .map(g -> new GenerateurDetailReponse(g.id(), g.code(), g.designation(), g.statut())).toList());
    }
}
