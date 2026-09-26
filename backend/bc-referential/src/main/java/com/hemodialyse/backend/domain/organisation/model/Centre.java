package com.hemodialyse.backend.domain.organisation.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Entité — un centre d'hémodialyse, rattaché à exactement une société.
 * <p>
 * Les modifications qui touchent à la présence du centre dans la société (désactivation, retrait) passent par
 * l'agrégat {@link Societe}, qui garantit qu'il lui reste toujours au moins un centre actif.
 */
public class Centre {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9_-]{1,29}");

    private final UUID id;
    private String code;
    private String nom;
    private Coordonnees coordonnees;
    private boolean actif;

    public Centre(UUID id, String code, String nom, Coordonnees coordonnees, boolean actif) {
        if (id == null) throw new IllegalArgumentException("id du centre obligatoire");
        this.id = id;
        renommer(code, nom);
        this.coordonnees = coordonnees == null ? Coordonnees.VIDES : coordonnees;
        this.actif = actif;
    }

    public static Centre creer(String code, String nom, Coordonnees coordonnees) {
        return new Centre(UUID.randomUUID(), code, nom, coordonnees, true);
    }

    void renommer(String code, String nom) {
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(normalizedCode).matches()) {
            throw new BusinessException("CENTRE_CODE_INVALIDE",
                    "Code de centre invalide (2 à 30 caractères : lettres, chiffres, tiret, souligné)");
        }
        String normalizedName = nom == null ? "" : nom.trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 150) {
            throw new BusinessException("CENTRE_NOM_INVALIDE", "Nom de centre obligatoire (150 caractères maximum)");
        }
        this.code = normalizedCode;
        this.nom = normalizedName;
    }

    void modifier(String code, String nom, Coordonnees coordonnees) {
        renommer(code, nom);
        this.coordonnees = coordonnees == null ? Coordonnees.VIDES : coordonnees;
    }

    void activer() {
        this.actif = true;
    }

    void desactiver() {
        this.actif = false;
    }

    public UUID id() {
        return id;
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

    public boolean actif() {
        return actif;
    }
}
