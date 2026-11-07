package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AntecedentIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedents;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Résout un antécédent à une date (absente ou future acceptée : reproduit, KB-MED-65). Déjà résolu : 422 {@code ANTECEDENT_DEJA_RESOLU}.
 * Antécédent inconnu, d'un autre patient ou d'un autre centre : refus 400 (reproduit, KB-MED-60). Droit : MEDECIN.
 */
public class ResoudreUnAntecedent {
    private final SessionCourante session;
    private final Antecedents antecedents;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public ResoudreUnAntecedent(SessionCourante session, Antecedents antecedents, UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        this.session = session;
        this.antecedents = antecedents;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public Antecedent executer(UUID centreDemande, UUID patientDemande, UUID id, LocalDate dateDeResolution) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> {
            Antecedent antecedent = antecedents.trouverEnVerrouillant(centre, patient, id).orElseThrow(AntecedentIntrouvable::new);
            antecedent.resoudre(dateDeResolution, horloge.maintenant());
            antecedents.enregistrerLaCorrection(antecedent);
            return antecedent;
        });
    }
}
