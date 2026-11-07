package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieDEtape;

import java.util.UUID;

/** Corps de l'ajout d'une étape (mêmes champs que le legacy). Catégorie absente ou inconnue : 400. */
public record EtapeDeBilanDemande(UUID centerId, String categorie, String libelle) {
    public CategorieDEtape categorieLue() {
        return Enumerations.obligatoire(CategorieDEtape.class, categorie, "categorie");
    }
}
