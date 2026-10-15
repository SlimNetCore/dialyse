package com.hemodialyse.backend.contexts.comptabilite.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié du plan comptable (RG-CPT-018) : le plan effectif d'un centre, en lecture. Tant que le centre n'a rien
 * enregistré, c'est le plan de départ (complété des comptes de son paramétrage).
 */
public interface PlanComptableEnLecture {

    /**
     * Vrai si le numéro (casse et espaces de bord ignorés) est un compte actif du plan du centre.
     */
    boolean estUnCompteActif(UUID centre, String numero);
}
