package com.hemodialyse.backend.domain.infirmier.model;

import java.util.UUID;

/**
 * Infirmier (ou aide-soignant) d'un centre : référentiel du personnel soignant affecté aux séances. Un infirmier
 * {@code habiliteIsolement} peut travailler en salle d'isolement (patients à risque infectieux). Il peut être relié à
 * un compte utilisateur ({@code userId}, facultatif) : tous les infirmiers n'ont pas un accès à l'application.
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

    public static Infirmier creer(UUID centerId, String matricule, String nom, String prenom, String telephone,
                                  QualificationInfirmier qualification, boolean habiliteIsolement) {
        return new Infirmier(UUID.randomUUID(), centerId, matricule, nom, prenom, telephone, qualification,
                habiliteIsolement, true, null);
    }

    public Infirmier modifier(String matricule, String nom, String prenom, String telephone,
                              QualificationInfirmier qualification, boolean habiliteIsolement) {
        return new Infirmier(id, centerId, matricule, nom, prenom, telephone, qualification, habiliteIsolement, actif,
                userId);
    }

    public Infirmier desactiver() {
        return avecActif(false);
    }

    public Infirmier reactiver() {
        return avecActif(true);
    }

    /**
     * Relie la fiche à un compte utilisateur.
     *
     * @throws IllegalStateException la fiche est déjà reliée à un compte
     */
    public Infirmier lierCompte(UUID compteId) {
        if (compteId == null) throw new IllegalArgumentException("Compte requis");
        if (userId != null) throw new IllegalStateException("La fiche est déjà reliée à un compte");
        return new Infirmier(id, centerId, matricule, nom, prenom, telephone, qualification, habiliteIsolement, actif,
                compteId);
    }

    public Infirmier delierCompte() {
        return new Infirmier(id, centerId, matricule, nom, prenom, telephone, qualification, habiliteIsolement, actif,
                null);
    }

    /**
     * « Prénom Nom », ou le nom seul sans prénom.
     */
    public String nomComplet() {
        return prenom == null ? nom : prenom + " " + nom;
    }

    private Infirmier avecActif(boolean actif) {
        return new Infirmier(id, centerId, matricule, nom, prenom, telephone, qualification, habiliteIsolement, actif,
                userId);
    }
}
