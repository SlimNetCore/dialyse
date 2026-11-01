package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.application.LectureDesPreferences.LigneDInfirmier;

import java.util.UUID;

public record LigneInfirmierReponse(UUID infirmierId, String nom, String qualification, ProfilReponse profil) {
    public static LigneInfirmierReponse depuis(LigneDInfirmier l) {
        return new LigneInfirmierReponse(l.infirmier().valeur(), l.nom(), l.qualification(), ProfilReponse.depuis(l.profil()));
    }
}
