package com.hemodialyse.backend.contexts.comptabilite.domain.journal;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

import java.util.List;
import java.util.Optional;

/**
 * Port sortant : journaux comptables paramétrés par un centre. Toute lecture et toute écriture est bornée au centre.
 */
public interface JournalRepository {

    /**
     * Journaux enregistrés pour le centre, triés par code ; vide tant que le centre n'a rien paramétré.
     */
    List<Journal> duCentre(CentreId centre);

    Optional<Journal> trouver(CentreId centre, CodeJournal code);

    /**
     * Crée le journal ou met à jour celui qui porte ce code dans le centre.
     */
    void enregistrer(Journal journal);

    void supprimer(CentreId centre, CodeJournal code);
}
