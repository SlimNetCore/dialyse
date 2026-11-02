package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;

import java.util.regex.Pattern;

/**
 * Agrégat {@code Centre} : un centre de dialyse appartient à exactement une société.
 * Coordonnées de contact (RG-ORG-020 à RG-ORG-022) et paramètres d'exploitation.
 */
public class Centre {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9_\\-]{1,29}");

    private final CentreId id;
    private SocieteId societeId;
    private String code;
    private String nom;
    private Coordonnees coordonnees;
    private String fuseauHoraire;
    private boolean actif;

    public Centre(CentreId id, SocieteId societeId, String code, String nom, Coordonnees coordonnees,
                  String fuseauHoraire, boolean actif) {
        if (id == null) throw new IllegalArgumentException("Centre requis");
        if (societeId == null) throw new IllegalArgumentException("Societe requise");
        if (code == null || !CODE.matcher(code.trim()).matches()) {
            throw new IllegalArgumentException("CENTRE_CODE_INVALIDE");
        }
        if (nom == null || nom.isBlank() || nom.trim().length() > 150) {
            throw new IllegalArgumentException("CENTRE_NOM_INVALIDE");
        }
        this.id = id;
        this.societeId = societeId;
        this.code = code.trim();
        this.nom = nom.trim();
        this.coordonnees = coordonnees == null ? Coordonnees.VIDES : coordonnees;
        this.fuseauHoraire = fuseauHoraire == null || fuseauHoraire.isBlank() ? "UTC" : fuseauHoraire;
        this.actif = actif;
    }

    public CentreId id() {
        return id;
    }

    public SocieteId societeId() {
        return societeId;
    }

    public String code() {
        return code;
    }

    public String nom() {
        return nom;
    }

    public Coordonnees coordonnees() {
        return coordonnees;
    }

    public String fuseauHoraire() {
        return fuseauHoraire;
    }

    public boolean actif() {
        return actif;
    }

    public void modifier(String code, String nom, Coordonnees coordonnees) {
        modifierParametres(code, nom, coordonnees, this.fuseauHoraire, this.actif);
    }

    /** Réactive le centre (idempotent). */
    public void activer() {
        this.actif = true;
    }

    /**
     * Désactive le centre. Invariant de la société : elle conserve au moins un centre actif.
     *
     * @param centresActifsDeLaSociete les centres actifs de la société du centre (lus sous le verrou de la société)
     * @throws IllegalArgumentException {@code SOCIETE_DERNIER_CENTRE} si ce centre est le dernier centre actif
     */
    public void desactiver(java.util.List<Centre> centresActifsDeLaSociete) {
        if (actif) garantirNEtrePasLeDernierCentreActif(centresActifsDeLaSociete);
        this.actif = false;
    }

    /**
     * Transfère le centre vers une autre société active, sans priver la société d'origine de son dernier centre actif.
     *
     * @throws IllegalArgumentException {@code CENTRE_TRANSFERT_MEME_SOCIETE}, {@code SOCIETE_INACTIVE} ou {@code SOCIETE_DERNIER_CENTRE}
     */
    public void transfererVers(Societe cible, java.util.List<Centre> centresActifsDeLaSocieteDOrigine) {
        if (societeId.equals(cible.id())) throw new IllegalArgumentException("CENTRE_TRANSFERT_MEME_SOCIETE");
        if (!cible.actif()) throw new IllegalArgumentException("SOCIETE_INACTIVE");
        if (actif) garantirNEtrePasLeDernierCentreActif(centresActifsDeLaSocieteDOrigine);
        this.societeId = cible.id();
    }

    private void garantirNEtrePasLeDernierCentreActif(java.util.List<Centre> centresActifsDeLaSociete) {
        if (centresActifsDeLaSociete.stream().allMatch(a -> a.id().equals(id))) {
            throw new IllegalArgumentException("SOCIETE_DERNIER_CENTRE");
        }
    }

    public void modifierParametres(String code, String nom, Coordonnees coordonnees, String fuseauHoraire, boolean actif) {
        if (code != null && !code.isBlank()) {
            if (!CODE.matcher(code.trim()).matches()) throw new IllegalArgumentException("CENTRE_CODE_INVALIDE");
            this.code = code.trim();
        }
        if (nom != null && !nom.isBlank()) {
            if (nom.trim().length() > 150) throw new IllegalArgumentException("CENTRE_NOM_INVALIDE");
            this.nom = nom.trim();
        }
        if (coordonnees != null) this.coordonnees = coordonnees;
        if (fuseauHoraire != null && !fuseauHoraire.isBlank()) this.fuseauHoraire = fuseauHoraire;
        this.actif = actif;
    }

    public void transferer(SocieteId nouvelleSociete) {
        if (nouvelleSociete == null) throw new IllegalArgumentException("Societe requise");
        this.societeId = nouvelleSociete;
    }
}
