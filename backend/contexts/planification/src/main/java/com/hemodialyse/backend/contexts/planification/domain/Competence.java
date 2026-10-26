package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Compétence particulière d'un infirmier, exigée sur une case dès qu'un patient qui la requiert y dialyse :
 * {@code PEDIATRIE} pour un patient de moins de 18 ans, {@code CATHETER} pour un patient dont l'abord vasculaire actif est
 * un cathéter (RG-PLN-097).
 */
public enum Competence {
    PEDIATRIE,
    CATHETER
}
