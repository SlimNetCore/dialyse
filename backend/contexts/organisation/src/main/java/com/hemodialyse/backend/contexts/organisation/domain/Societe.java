package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;

import java.time.OffsetDateTime;
import java.util.regex.Pattern;

/**
 * Agrégat racine {@code Societe} : une société chapeaute un ou plusieurs centres.
 * <p>
 * Identité légale (RG-ORG-002 à RG-ORG-005) : code normalisé, raison sociale, NIF/NIS/RC
 * facultatifs, coordonnées de contact et pied de page des documents.
 */
public class Societe {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9_\\-]{1,29}");

    private final SocieteId id;
    private final OffsetDateTime createdAt;
    private String code;
    private String raisonSociale;
    private String nif;
    private String nis;
    private String rc;
    private Coordonnees coordonnees;
    private String piedDePage;
    private boolean actif;

    public Societe(SocieteId id, String code, String raisonSociale, String nif, String nis, String rc,
                   Coordonnees coordonnees, String piedDePage, boolean actif, OffsetDateTime createdAt) {
        if (id == null) throw new IllegalArgumentException("Societe requise");
        if (createdAt == null) throw new IllegalArgumentException("createdAt requis");
        this.id = id;
        this.createdAt = createdAt;
        this.actif = actif;
        appliquerIdentite(code, raisonSociale, nif, nis, rc, coordonnees, piedDePage);
    }

    private static String identifiantLegal(String valeur, String libelle) {
        if (valeur == null || valeur.isBlank()) return null;
        String normalisee = valeur.trim();
        if (normalisee.length() > 40) {
            throw new IllegalArgumentException("SOCIETE_IDENTIFIANT_INVALIDE");
        }
        return normalisee;
    }

    private void appliquerIdentite(String code, String raisonSociale, String nif, String nis, String rc,
                                   Coordonnees coordonnees, String piedDePage) {
        if (code == null || !CODE.matcher(code.trim()).matches()) {
            throw new IllegalArgumentException("SOCIETE_CODE_INVALIDE");
        }
        if (raisonSociale == null || raisonSociale.isBlank() || raisonSociale.trim().length() > 200) {
            throw new IllegalArgumentException("SOCIETE_NOM_INVALIDE");
        }
        if (piedDePage != null && piedDePage.length() > 500) {
            throw new IllegalArgumentException("SOCIETE_PIED_PAGE_TROP_LONG");
        }
        this.code = code.trim();
        this.raisonSociale = raisonSociale.trim();
        this.nif = identifiantLegal(nif, "NIF");
        this.nis = identifiantLegal(nis, "NIS");
        this.rc = identifiantLegal(rc, "RC");
        this.coordonnees = coordonnees == null ? Coordonnees.VIDES : coordonnees;
        this.piedDePage = piedDePage == null || piedDePage.isBlank() ? null : piedDePage;
    }

    public SocieteId id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String raisonSociale() {
        return raisonSociale;
    }

    public String nif() {
        return nif;
    }

    public String nis() {
        return nis;
    }

    public String rc() {
        return rc;
    }

    public Coordonnees coordonnees() {
        return coordonnees;
    }

    public String piedDePage() {
        return piedDePage;
    }

    public boolean actif() {
        return actif;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public void modifier(String code, String raisonSociale, boolean actif) {
        modifier(code, raisonSociale, nif, nis, rc, coordonnees, piedDePage, actif);
    }

    public void modifier(String code, String raisonSociale, String nif, String nis, String rc,
                         Coordonnees coordonnees, String piedDePage, boolean actif) {
        appliquerIdentite(code, raisonSociale, nif, nis, rc, coordonnees, piedDePage);
        this.actif = actif;
    }
}
