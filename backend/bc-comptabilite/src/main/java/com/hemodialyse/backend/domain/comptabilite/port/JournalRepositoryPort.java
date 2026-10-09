package com.hemodialyse.backend.domain.comptabilite.port;

import com.hemodialyse.backend.domain.comptabilite.valueobject.Journal;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;

import java.util.List;
import java.util.UUID;

/**
 * Port sortant — journaux comptables paramétrés par un centre.
 */
public interface JournalRepositoryPort {

    /**
     * Journaux enregistrés pour le centre, triés par code ; vide tant que le centre n'a rien paramétré.
     */
    List<Journal> findByCenter(UUID centerId);

    /**
     * Crée le journal ou met à jour celui qui porte ce code dans le centre.
     */
    void save(UUID centerId, Journal journal);

    void delete(UUID centerId, JournalCode code);
}
