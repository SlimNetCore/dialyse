package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.Competence;
import com.hemodialyse.backend.contexts.planification.domain.ProfilDePlanification;

import java.util.List;
import java.util.UUID;

public record ProfilReponse(UUID infirmierId, int tauxActivite, List<Competence> competences) {
    public static ProfilReponse depuis(ProfilDePlanification p) {
        return new ProfilReponse(p.infirmier().valeur(), p.tauxActivite(), p.competences().stream().sorted().toList());
    }
}
