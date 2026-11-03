package com.hemodialyse.backend.contexts.patientcouverture.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ligne de la liste des patients. L'ordre et les noms des champs sont ceux du JSON actuel (contrat inchangé) ; les indicateurs de
 * jour sont nuls pour une ligne héritée aux colonnes nulles.
 */
public record LignePatient(UUID id, String codePatient, String nom, String prenom, String sexe, LocalDate dateAdmission,
                           String numeroAssurance, String etatPatient, String dateEvenementEtat, String dateEvenement,
                           UUID medecinTraitantId, String medecinTraitantNom, UUID positionId, String positionCode,
                           String positionLibelle, UUID transporteurAllerId, String transporteurAllerNom,
                           UUID transporteurRetourId, String transporteurRetourNom, Boolean jourDimanche, Boolean jourLundi,
                           Boolean jourMardi, Boolean jourMercredi, Boolean jourJeudi, Boolean jourVendredi,
                           Boolean jourSamedi, boolean nonFacturable, String pecStatus, UUID pecForfaitId,
                           String pecForfaitCode, String pecForfaitLibelle) {
}
