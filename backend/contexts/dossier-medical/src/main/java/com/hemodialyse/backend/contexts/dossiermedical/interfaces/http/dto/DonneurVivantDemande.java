package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.LienDeParente;

import java.time.LocalDate;
import java.util.UUID;

/** Corps de l'enregistrement d'un donneur vivant (mêmes champs que le legacy). Lien de parenté absent ou inconnu : 400. */
public record DonneurVivantDemande(UUID centerId, String nom, String prenom, LocalDate dateNaissance, String lienParente, String telephone,
                                   String groupeSanguin, String typageHla) {
    public LienDeParente lienLu() {
        return Enumerations.obligatoire(LienDeParente.class, lienParente, "lienParente");
    }
}
