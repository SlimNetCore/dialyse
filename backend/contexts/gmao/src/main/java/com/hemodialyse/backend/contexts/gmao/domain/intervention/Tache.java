package com.hemodialyse.backend.contexts.gmao.domain.intervention;

/**
 * Tâche d'une intervention (R21) : une étape de travail décrite, qui avance de {@code A_FAIRE} à {@code COMPLETEE}.
 * Entité interne de l'agrégat {@link Intervention} : elle ne se manipule que par la racine. Description rognée, jamais vide.
 */
public final class Tache {

    private final TacheId id;
    private final String description;
    private final Jalon creation;
    private StatutTache statut;
    private Jalon demarrage;
    private Jalon achevement;

    private Tache(TacheId id, String description, StatutTache statut, Jalon creation, Jalon demarrage, Jalon achevement) {
        this.id = id;
        this.description = description;
        this.statut = statut;
        this.creation = creation;
        this.demarrage = demarrage;
        this.achevement = achevement;
    }

    static Tache ajouter(TacheId id, String description, Jalon creation) {
        if (description == null || description.isBlank()) throw new ChampInvalide("Description de la tâche requise");
        return new Tache(id, description.trim(), StatutTache.A_FAIRE, creation, null, null);
    }

    /** Reconstitution depuis la persistance (adapters seulement) : aucune règle rejouée. */
    public static Tache reconstituer(TacheId id, String description, StatutTache statut, Jalon creation, Jalon demarrage,
                                     Jalon achevement) {
        return new Tache(id, description, statut, creation, demarrage, achevement);
    }

    void demarrer(Jalon jalon) {
        if (statut != StatutTache.A_FAIRE) throw TransitionDeTacheInvalide.pourDemarrer(statut);
        statut = StatutTache.EN_COURS;
        demarrage = jalon;
    }

    void achever(Jalon jalon) {
        if (statut != StatutTache.EN_COURS) throw TransitionDeTacheInvalide.pourAchever(statut);
        statut = StatutTache.COMPLETEE;
        achevement = jalon;
    }

    public TacheId id() {
        return id;
    }

    public String description() {
        return description;
    }

    public StatutTache statut() {
        return statut;
    }

    public Jalon creation() {
        return creation;
    }

    public Jalon demarrage() {
        return demarrage;
    }

    public Jalon achevement() {
        return achevement;
    }
}
