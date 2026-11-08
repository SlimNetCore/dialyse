package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.RappelDeRegularisation;

/**
 * Port sortant : la diffusion et le journal durable du rappel à l'administrateur du centre ({@code SEANCES_A_REGULARISER}, cible
 * {@code ADMIN}). Une seule émission par centre et par jour du centre : un rappel déjà émis ce jour-là n'est ni renvoyé ni dupliqué
 * (KB-SEA-35, corrigé), même si deux instances du serveur le déclenchent ensemble.
 */
public interface RappelsDeRegularisation {

    /** @return vrai si ce passage a émis le rappel, faux s'il l'était déjà pour ce centre et ce jour */
    boolean rappeler(RappelDeRegularisation rappel);
}
