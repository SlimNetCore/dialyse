package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Médecin référencé par un centre. Un médecin n'est jamais supprimé (il est référencé par les patients et les prescriptions) :
 * il est désactivé et disparaît des listes de sélection.
 */
public class Medecin {
    private final MedecinId id;
    private final CentreId centreId;
    private String nom;
    private String prenom;
    private String specialite;
    private boolean actif;

    public Medecin(MedecinId id, CentreId centreId, String nom, String prenom, String specialite) {
        this(id, centreId, nom, prenom, specialite, true);
    }

    public Medecin(MedecinId id, CentreId centreId, String nom, String prenom, String specialite, boolean actif) {
        if (id == null) throw new IllegalArgumentException("Medecin requis");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (nom == null || nom.isBlank()) throw new IllegalArgumentException("Nom requis");
        this.id = id;
        this.centreId = centreId;
        this.nom = nom;
        this.prenom = prenom;
        this.specialite = specialite;
        this.actif = actif;
    }

    public MedecinId id() {
        return id;
    }

    public CentreId centreId() {
        return centreId;
    }

    public String nom() {
        return nom;
    }

    public String prenom() {
        return prenom;
    }

    public String specialite() {
        return specialite;
    }

    public boolean actif() {
        return actif;
    }

    public void modifier(String nom, String prenom, String specialite) {
        if (nom != null && !nom.isBlank()) this.nom = nom;
        if (prenom != null) this.prenom = prenom;
        if (specialite != null) this.specialite = specialite;
    }

    /** Désactive le médecin (idempotent) : il n'est plus proposé, mais ses références restent valides (D5). */
    public void desactiver() {
        this.actif = false;
    }

    /** Corrige l'identité et la spécialité (remplacement complet, comme l'administration des référentiels l'a toujours fait). */
    public void corriger(SaisieDeMedecin saisie) {
        this.nom = saisie.nom();
        this.prenom = saisie.prenom();
        this.specialite = saisie.specialite();
    }

    /** Un médecin désactivé dont le nom et le prénom sont saisis à nouveau redevient proposé, avec la saisie (D6). */
    public void reactiver(SaisieDeMedecin saisie) {
        corriger(saisie);
        this.actif = true;
    }

    /** Clé d'unicité : nom et prénom, casse et espaces ignorés, un prénom absent valant vide. */
    public String cle() {
        return SaisieDeMedecin.cleDe(nom, prenom);
    }

    /**
     * Une seule règle d'unicité par centre (D7) : un nom et un prénom désignent un seul médecin proposé.
     *
     * @param autresDuCentre les médecins proposés (actifs) du centre
     * @throws MedecinDejaExistant si un autre médecin porte déjà ce nom et ce prénom
     */
    public void verifierLUnicite(java.util.List<Medecin> autresDuCentre) {
        String cle = cle();
        boolean pris = autresDuCentre.stream().anyMatch(m -> !m.id().equals(id) && m.cle().equals(cle));
        if (pris) throw new MedecinDejaExistant("Un élément de « Médecins traitants » avec ce nom + prénom existe déjà.");
    }
}
