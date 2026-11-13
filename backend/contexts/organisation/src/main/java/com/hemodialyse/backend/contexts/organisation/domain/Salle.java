package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/**
 * Salle d'un centre. Une salle utilisée n'est jamais supprimée (D4, PL11-4, ADR 0160) : elle est désactivée et n'est plus proposée,
 * mais l'historique (patients, séances, plannings, équipements) la retrouve.
 */
public class Salle {
    private final SalleId id;
    private final CentreId centreId;
    private String code;
    private String nom;
    private boolean ouverte;
    private boolean isolement;
    private Integer capacite;
    private boolean actif;

    public Salle(SalleId id, CentreId centreId, String code, String nom, boolean ouverte) {
        this(id, centreId, code, nom, ouverte, false, null, true);
    }

    public Salle(SalleId id, CentreId centreId, String code, String nom, boolean ouverte, boolean isolement, Integer capacite,
                 boolean actif) {
        if (id == null) throw new IllegalArgumentException("Salle requise");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Code requis");
        if (nom == null || nom.isBlank()) throw new IllegalArgumentException("Nom requis");
        this.id = id;
        this.centreId = centreId;
        this.code = code;
        this.nom = nom;
        this.ouverte = ouverte;
        this.isolement = isolement;
        this.capacite = capacite;
        this.actif = actif;
    }

    /** Référence une nouvelle salle (ouverte) : sans précision, ce n'est pas une salle d'isolement et sa capacité est inconnue. */
    public static Salle referencer(SalleId id, CentreId centreId, SaisieDeSalle saisie) {
        return new Salle(id, centreId, saisie.code(), saisie.nom(), true, Boolean.TRUE.equals(saisie.isolement()), saisie.capacite(), true);
    }

    public SalleId id() {
        return id;
    }

    public CentreId centreId() {
        return centreId;
    }

    public String code() {
        return code;
    }

    public String nom() {
        return nom;
    }

    public boolean ouverte() {
        return ouverte;
    }

    public boolean isolement() {
        return isolement;
    }

    public Integer capacite() {
        return capacite;
    }

    public boolean actif() {
        return actif;
    }

    public void ouvrir() {
        this.ouverte = true;
    }

    public void fermer() {
        this.ouverte = false;
    }

    public void modifier(String code, String nom) {
        if (code != null && !code.isBlank()) this.code = code;
        if (nom != null && !nom.isBlank()) this.nom = nom;
    }

    /**
     * Corrige la salle avec la saisie : le code et le nom sont remplacés ; l'isolement et la capacité non précisés sont conservés (D8).
     * La salle reste ouverte ou fermée comme elle l'était.
     */
    public void corriger(SaisieDeSalle saisie) {
        this.code = saisie.code();
        this.nom = saisie.nom();
        if (saisie.isolement() != null) this.isolement = saisie.isolement();
        if (saisie.capacite() != null) this.capacite = saisie.capacite();
    }

    /** Désactive la salle (idempotent) : elle n'est plus proposée, mais ses références restent valides (D4). */
    public void desactiver() {
        this.actif = false;
    }

    /** Une salle désactivée dont le code est saisi à nouveau redevient proposée, avec la saisie (calque de D6). */
    public void reactiver(SaisieDeSalle saisie) {
        corriger(saisie);
        this.actif = true;
    }

    /** Clé d'unicité : le code, casse et espaces ignorés. */
    public String cle() {
        return SaisieDeCreneau.cleDe(code);
    }

    /**
     * @param autresDuCentre les salles proposées (actives) du centre
     * @throws SalleDejaExistante si une autre salle porte déjà ce code
     */
    public void verifierLUnicite(List<Salle> autresDuCentre) {
        String cle = cle();
        boolean prise = autresDuCentre.stream().anyMatch(s -> !s.id().equals(id) && s.cle().equals(cle));
        if (prise) throw new SalleDejaExistante("Un élément de « Salles » avec ce code existe déjà.");
    }
}
