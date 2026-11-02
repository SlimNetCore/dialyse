package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Trace immuable d'un changement de situation du patient. La place (salle, créneau, générateur, jours) est celle occupée au
 * moment du mouvement.
 *
 * @param etatPrecedent nul à l'admission
 */
public record MouvementDuPatient(UUID id, PatientId patient, CentreId centre, TypeDeMouvement type, LocalDate dateEffet,
                                 EtatDuPatient etatPrecedent, EtatDuPatient etatNouveau, UUID salleId, UUID creneauId,
                                 UUID generateurId, String joursDeDialyse, boolean automatique, Instant creeLe) {
}
