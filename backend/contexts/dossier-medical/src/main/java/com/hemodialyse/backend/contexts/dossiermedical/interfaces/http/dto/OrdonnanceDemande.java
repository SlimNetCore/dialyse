package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.application.RedigerUneOrdonnance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Corps de la création d'une ordonnance (mêmes champs que le legacy). */
public record OrdonnanceDemande(UUID centerId, String medecinId, LocalDate datePrescription, List<LigneDemande> lignes) {

    public record LigneDemande(String codeSystem, String code, String codeDisplay, String libelle, String posologie, String voie,
                               Integer dureeJours, Integer quantite, String instructions) {
    }

    public List<RedigerUneOrdonnance.LigneDemandee> lignesDemandees() {
        if (lignes == null) return null;
        return lignes.stream().map(l -> l == null ? null : new RedigerUneOrdonnance.LigneDemandee(l.codeSystem(), l.code(),
                l.codeDisplay(), l.libelle(), l.posologie(), l.voie(), l.dureeJours(), l.quantite(), l.instructions())).toList();
    }
}
