package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

public class Creneau {
    private final CreneauId id;
    private final CentreId centreId;
    private String code;
    private String libelle;
    private boolean actif;

    public Creneau(CreneauId id, CentreId centreId, String code, String libelle) {
        this(id, centreId, code, libelle, true);
    }

    public Creneau(CreneauId id, CentreId centreId, String code, String libelle, boolean actif) {
        if (id == null) throw new IllegalArgumentException("Creneau requis");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Code requis");
        if (libelle == null || libelle.isBlank()) throw new IllegalArgumentException("Libelle requis");
        this.id = id;
        this.centreId = centreId;
        this.code = code;
        this.libelle = libelle;
        this.actif = actif;
    }

    /** Référence un créneau dans un centre à partir d'une saisie contrôlée (PL11-2). */
    public static Creneau referencer(CreneauId id, CentreId centreId, SaisieDeCreneau saisie) {
        return new Creneau(id, centreId, saisie.code(), saisie.libelle());
    }

    public CreneauId id() {
        return id;
    }

    public CentreId centreId() {
        return centreId;
    }

    public String code() {
        return code;
    }

    public String libelle() {
        return libelle;
    }

    /** Un créneau désactivé n'est plus proposé ; les séances, patients et plannings qui le référencent le conservent (PL11-2, D4). */
    public boolean actif() {
        return actif;
    }

    public void modifier(String code, String libelle) {
        if (code != null && !code.isBlank()) this.code = code;
        if (libelle != null && !libelle.isBlank()) this.libelle = libelle;
    }

    /** Corrige le code et le libellé (remplacement complet, comme l'administration des référentiels l'a toujours fait). */
    public void corriger(SaisieDeCreneau saisie) {
        this.code = saisie.code();
        this.libelle = saisie.libelle();
    }

    /** Retire le créneau des propositions sans le supprimer : il est encore référencé. */
    public void desactiver() {
        this.actif = false;
    }

    /** Un créneau désactivé dont le code est saisi à nouveau redevient proposé, avec le libellé saisi. */
    public void reactiver(SaisieDeCreneau saisie) {
        corriger(saisie);
        this.actif = true;
    }

    public String cle() {
        return SaisieDeCreneau.cleDe(code);
    }

    /**
     * Une seule règle d'unicité par centre : un code (casse et espaces ignorés) désigne un seul créneau proposé.
     *
     * @param autresDuCentre les créneaux proposés du centre
     * @throws CreneauDejaExistant si un autre créneau porte déjà ce code
     */
    public void verifierLUnicite(List<Creneau> autresDuCentre) {
        String cle = cle();
        boolean pris = autresDuCentre.stream().anyMatch(c -> !c.id().equals(id) && c.cle().equals(cle));
        if (pris) throw new CreneauDejaExistant("Un élément de « Créneaux » avec ce code existe déjà.");
    }
}
