package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.LienDeParente;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatDeCrossmatch;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDuBilanDuDonneur;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Corps de la mise à jour d'un donneur vivant (mêmes champs que le legacy ; un champ absent est effacé). Lien, statut du bilan et
 * crossmatch absents ou inconnus : 400 (KB-MED-87 : le crossmatch absent donnait 500, le défaut {@code NON_FAIT} du domaine étant
 * inatteignable ; il reste refusé, non défaut).
 */
public record MiseAJourDeDonneurDemande(UUID centerId, String nom, String prenom, LocalDate dateNaissance, String lienParente, String telephone,
                                        String groupeSanguin, String typageHla, String statutBilan, String crossmatchResultat,
                                        LocalDate dateCrossmatch, String bilanRealise, String contreIndications, String decisionFinale,
                                        LocalDate dateDecision) {
    public LienDeParente lienLu() {
        return Enumerations.obligatoire(LienDeParente.class, lienParente, "lienParente");
    }

    public StatutDuBilanDuDonneur statutLu() {
        return Enumerations.obligatoire(StatutDuBilanDuDonneur.class, statutBilan, "statutBilan");
    }

    public ResultatDeCrossmatch crossmatchLu() {
        return Enumerations.obligatoire(ResultatDeCrossmatch.class, crossmatchResultat, "crossmatchResultat");
    }
}
