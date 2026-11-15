package com.hemodialyse.backend.contexts.dossiermedical.application;

import java.time.LocalDate;

/**
 * Une ligne de l'état imprimé d'une ordonnance (DOC5d, ADR 0196) : l'identité du patient, le centre, l'en-tête de l'ordonnance et UNE ligne
 * de médicament, valeurs telles qu'enregistrées (nulles possibles, imprimées comme avant). Le gabarit imprime le titre et l'en-tête de la
 * première ligne, puis une ligne de détail par enregistrement.
 */
public record LigneDeLOrdonnanceImprimee(String nom, String prenom, LocalDate dateNaissance, String numeroAssurance, String nomDuCentre,
                                         String numero, LocalDate datePrescription, String medecin, String medicament, String posologie,
                                         String voie, Integer dureeJours, Integer quantite, String instructions) {
}
