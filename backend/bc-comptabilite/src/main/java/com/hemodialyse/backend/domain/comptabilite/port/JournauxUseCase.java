package com.hemodialyse.backend.domain.comptabilite.port;

import com.hemodialyse.backend.domain.comptabilite.valueobject.Journal;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;

import java.util.List;
import java.util.UUID;

/**
 * Port entrant — paramétrage des journaux comptables d'un centre.
 */
public interface JournauxUseCase {

    /**
     * Journaux du centre ; ceux proposés par défaut tant qu'il n'a rien paramétré.
     */
    List<Journal> lister(UUID centerId);

    /**
     * Crée un journal ou modifie le libellé / l'état de celui qui porte ce code.
     */
    Journal enregistrer(UUID centerId, Journal journal);

    void supprimer(UUID centerId, JournalCode code);
}
