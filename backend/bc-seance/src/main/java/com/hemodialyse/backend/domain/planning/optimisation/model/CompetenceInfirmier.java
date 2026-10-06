package com.hemodialyse.backend.domain.planning.optimisation.model;

/**
 * Compétence particulière d'un infirmier, exigée sur une case dès qu'un patient qui la requiert y dialyse :
 * {@code PEDIATRIE} pour un patient de moins de 18 ans, {@code CATHETER} pour un patient dont l'abord vasculaire actif
 * est un cathéter (tunnelisé ou aigu).
 */
public enum CompetenceInfirmier {
    PEDIATRIE,
    CATHETER
}
