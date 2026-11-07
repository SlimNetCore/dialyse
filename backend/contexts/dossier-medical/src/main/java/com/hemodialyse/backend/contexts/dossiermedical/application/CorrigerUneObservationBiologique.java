package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationsBiologiques;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ValeurMesuree;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Corrige la valeur d'une observation du patient : numérique (avec unité) ou textuelle, jamais les deux ; le statut passe TOUJOURS à
 * CORRIGE ; code, date, statut et demande du corps sont ignorés ; une observation dérivée d'un bilan est immuable (422). Observation
 * inconnue, d'un autre patient ou d'un autre centre : refus 400 « Observation introuvable » (reproduit, KB-MED-76, à confirmer avec les
 * métiers). Verrou de la ligne : une écriture concurrente attend 5 s puis 409 (KB-MED-84, dernier écrivain gagnant avant). Droit : MEDECIN.
 */
public class CorrigerUneObservationBiologique {
    private final SessionCourante session;
    private final ObservationsBiologiques observations;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public CorrigerUneObservationBiologique(SessionCourante session, ObservationsBiologiques observations, UniteDeTravail uniteDeTravail,
                                            HorlogeDuCentre horloge) {
        this.session = session;
        this.observations = observations;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public ObservationBiologique executer(UUID centreDemande, UUID patientDemande, UUID id, BigDecimal valeurNumerique, String unite,
                                          String valeurTexte) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        ValeurMesuree valeur = valeurNumerique == null ? null : new ValeurMesuree(valeurNumerique, unite);
        return uniteDeTravail.executer(() -> {
            ObservationBiologique observation = observations.trouverEnVerrouillant(centre, patient, id).orElseThrow(ObservationIntrouvable::new);
            observation.corriger(valeur, valeurTexte, horloge.maintenant());
            observations.enregistrerLaCorrection(observation);
            return observation;
        });
    }
}
