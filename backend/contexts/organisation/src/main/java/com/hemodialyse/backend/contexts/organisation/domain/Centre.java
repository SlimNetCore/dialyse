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
