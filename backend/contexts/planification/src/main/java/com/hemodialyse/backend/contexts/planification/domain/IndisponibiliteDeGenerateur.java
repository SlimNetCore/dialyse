package com.hemodialyse.backend.contexts.planification.domain;

import java.util.List;

/**
 * Règle de l'alerte « générateur indisponible » (KB-GMAO-37, sans numéro de règle de gestion à ce jour : à ajouter au référentiel) : un générateur de dialyse qui <b>sort de
 * {@code EN_SERVICE}</b> (maintenance, attente de pièce, panne, réforme) prévient l'administration et le secrétariat, en citant
 * les patients dont c'est la place habituelle. Aucun déplacement automatique : les séances sont à déplacer par l'optimisation
 * « Maintenance des générateurs » (décision du 27/10, KB-PLN-63).
 */
public final class IndisponibiliteDeGenerateur {

    public static final String EN_SERVICE = "EN_SERVICE";
    /** Nombre de patients cités nommément dans l'alerte ; le total est donné à part. */
    public static final int PATIENTS_CITES = 5;

    private IndisponibiliteDeGenerateur() {
    }

    /**
     * @param statutPrecedent statut avant le changement ({@code null} : création, rien à signaler)
     * @param statut          statut atteint
     */
    public static boolean aSignaler(String statutPrecedent, String statut) {
        return EN_SERVICE.equals(statutPrecedent) && statut != null && !EN_SERVICE.equals(statut);
    }

    /** Les patients cités : les premiers par ordre alphabétique. */
    public static List<String> citer(List<String> nomsTries) {
        return nomsTries.stream().limit(PATIENTS_CITES).toList();
    }
}
