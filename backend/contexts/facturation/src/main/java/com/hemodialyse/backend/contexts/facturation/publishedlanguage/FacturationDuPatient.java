package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Langage publié (lecture) : ce que la Facturation sait d'un patient pour les autres contextes (Absences).
 */
public interface FacturationDuPatient {

    /**
     * Vrai si une facture du patient, dans ce centre, couvre le jour (bornes de la période incluses, quel que soit le statut).
     */
    boolean periodeFacturee(UUID centre, UUID patient, LocalDate jour);
}
