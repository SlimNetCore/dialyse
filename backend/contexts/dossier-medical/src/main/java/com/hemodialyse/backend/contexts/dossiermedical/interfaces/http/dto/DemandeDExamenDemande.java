package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.application.DemanderUnExamen;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Corps de la demande d'un examen (mêmes champs que le legacy). {@code urgent} est un booléen requis : absent ou nul, c'est un 400. */
public record DemandeDExamenDemande(UUID centerId, String prescripteurId, LocalDate dateDemande, String categorie, Boolean urgent,
                                    String motif, List<LigneDemande> lignes) {

    public record LigneDemande(String codeSystem, String code, String codeDisplay, String libelle, String commentaire) {
    }

    public List<DemanderUnExamen.LigneDemandee> lignesDemandees() {
        if (lignes == null) return null;
        return lignes.stream().map(l -> l == null ? null : new DemanderUnExamen.LigneDemandee(l.codeSystem(), l.code(), l.codeDisplay(),
                l.libelle(), l.commentaire())).toList();
    }
}
