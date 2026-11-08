package com.hemodialyse.backend.contexts.seance.domain;

/**
 * Motif obligatoire de la suppression d'une séance (erreur de saisie, doublon, mauvais patient...), conservé au journal des
 * suppressions. Nettoyé (espaces de début et de fin retirés) AVANT la mesure et le stockage ; de 5 à 500 caractères. Le refus est un 422
 * {@code SEANCE_SUPPRESSION_MOTIF_INVALIDE} (reproduit du legacy).
 */
public record MotifDeSuppression(String valeur) {

    public static final int LONGUEUR_MIN = 5;
    public static final int LONGUEUR_MAX = 500;

    public MotifDeSuppression {
        valeur = valeur == null ? "" : valeur.trim();
        if (valeur.length() < LONGUEUR_MIN || valeur.length() > LONGUEUR_MAX) {
            throw new SeanceRefusee(MotifDeRefus.MOTIF_DE_SUPPRESSION_INVALIDE,
                    "Le motif de suppression doit compter de " + LONGUEUR_MIN + " à " + LONGUEUR_MAX + " caractères");
        }
    }
}
