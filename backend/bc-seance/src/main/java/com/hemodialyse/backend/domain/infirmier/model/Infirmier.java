package com.hemodialyse.backend.domain.infirmier.model;

import java.util.UUID;

/**
 * Infirmier (ou aide-soignant) d'un centre, tel que le roulement, les absences, les remplacements et la présence le lisent
 * (slices P3 et P4). Un infirmier {@code habiliteIsolement} peut travailler en salle d'isolement (patients à risque
 * infectieux). Il peut être relié à un compte utilisateur ({@code userId}, facultatif). Le référentiel (création,
 * modification, activation, comptes) est servi par le contexte Planification (slice P2) : ce modèle est en lecture seule.
 */
public record Infirmier(
        UUID id,
        UUID centerId,
        String matricule,
        String nom,
        String prenom,
        String telephone,
        QualificationInfirmier qualification,
        boolean habiliteIsolement,
        boolean actif,
        UUID userId
) {
    public Infirmier {
        if (centerId == null) throw new IllegalArgumentException("Centre requis");
        if (matricule == null || matricule.isBlank()) throw new IllegalArgumentException("Matricule requis");
        if (nom == null || nom.isBlank()) throw new IllegalArgumentException("Nom requis");
        if (qualification == null) throw new IllegalArgumentException("Qualification requise");
        matricule = matricule.trim();
        nom = nom.trim();
        prenom = prenom == null || prenom.isBlank() ? null : prenom.trim();
        telephone = telephone == null || telephone.isBlank() ? null : telephone.trim();
    }

    /**
     * Infirmier sans compte utilisateur.
     */
    public Infirmier(UUID id, UUID centerId, String matricule, String nom, String prenom, String telephone,
                     QualificationInfirmier qualification, boolean habiliteIsolement, boolean actif) {
        this(id, centerId, matricule, nom, prenom, telephone, qualification, habiliteIsolement, actif, null);
    }

    /**
     * « Prénom Nom », ou le nom seul sans prénom.
     */
    public String nomComplet() {
        return prenom == null ? nom : prenom + " " + nom;
    }
}
