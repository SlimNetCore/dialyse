package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.UUID;

/**
 * Une ligne d'une demande d'examen : l'examen demandé, par son code, son libellé ou les deux. N'existe qu'au sein de la demande
 * (pas de cycle de vie propre, pas de colonne de centre : ADR 0072).
 */
public record LigneDeDemandeDExamen(UUID id, CodeDExamen code, String libelle, String commentaire) {

    public LigneDeDemandeDExamen {
        if (id == null) throw new SaisieInvalide("Identifiant de ligne requis");
        if (libelle != null && libelle.length() > 255) throw new SaisieInvalide("Le libellé de la ligne est limité à 255 caractères");
        if (commentaire != null && commentaire.length() > 500) throw new SaisieInvalide("Le commentaire de la ligne est limité à 500 caractères");
    }

    /** Ligne nouvelle : un code ou, à défaut, un libellé non blanc est obligatoire (422). */
    public static LigneDeDemandeDExamen nouvelle(UUID id, CodeDExamen code, String libelle, String commentaire) {
        if (code == null && (libelle == null || libelle.isBlank())) {
            throw new RegleDeGestionViolee("LIGNE_DEMANDE_EXAMEN_REQUISE",
                    "Un code d'examen ou, à défaut, un libellé est obligatoire pour chaque ligne demandée");
        }
        return new LigneDeDemandeDExamen(id, code, libelle, commentaire);
    }
}
