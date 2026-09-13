package com.hemodialyse.backend.domain.medical.examen.entity;

import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.util.UUID;

/**
 * Entité (AGENTS.md §14) — une ligne d'une demande d'examen : l'analyte ou l'acte demandé.
 * N'a de sens qu'à l'intérieur de l'agrégat {@code DemandeExamen} (pas de cycle de vie propre).
 */
public final class LigneDemandeExamen {

    private final UUID id;
    private final ConceptCode analyte;
    private final String libelle;
    private final String commentaire;

    private LigneDemandeExamen(UUID id, ConceptCode analyte, String libelle, String commentaire) {
        this.id = id;
        this.analyte = analyte;
        this.libelle = libelle;
        this.commentaire = commentaire;
    }

    public static LigneDemandeExamen creer(ConceptCode analyte, String libelle, String commentaire) {
        if (analyte == null && (libelle == null || libelle.isBlank())) {
            throw new BusinessException("LIGNE_DEMANDE_EXAMEN_REQUISE",
                    "Un code LOINC ou, à défaut, un libellé est obligatoire pour chaque ligne demandée");
        }
        return new LigneDemandeExamen(UUID.randomUUID(), analyte, libelle, commentaire);
    }

    public static LigneDemandeExamen reconstituer(UUID id, ConceptCode analyte, String libelle, String commentaire) {
        return new LigneDemandeExamen(id, analyte, libelle, commentaire);
    }

    public UUID getId() {
        return id;
    }

    public ConceptCode getAnalyte() {
        return analyte;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getCommentaire() {
        return commentaire;
    }
}
