package com.hemodialyse.backend.contexts.seance.domain;

/**
 * Compte qui appose une signature (infirmier, médecin) ou un déverrouillage : au plus 100 caractères (colonne {@code varchar(100)},
 * KB-SEA-11 : au-delà le legacy répondait 500, désormais 400). Absent : {@code system}, comme le legacy ; vide : conservé tel quel.
 */
public record Signataire(String valeur) {

    public static final int LONGUEUR_MAXIMALE = 100;
    public static final Signataire SYSTEME = new Signataire("system");

    public Signataire {
        if (valeur == null) throw new SaisieInvalide("Signataire requis");
        if (valeur.length() > LONGUEUR_MAXIMALE) throw new SaisieInvalide("Signataire trop long (100 caractères au plus)");
    }

    public static Signataire lire(String brut) {
        return brut == null ? SYSTEME : new Signataire(brut);
    }
}
