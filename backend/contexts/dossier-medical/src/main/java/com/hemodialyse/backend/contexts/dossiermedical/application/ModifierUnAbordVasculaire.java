package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordVasculaireIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordsVasculaires;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Remplace tous les champs d'un abord vasculaire du patient (absents = effacés, « actif » absent = actif : reproduit, KB-MED-62 résiduel,
 * à confirmer avec les métiers). Écart décidé (KB-MED-62, trou de sécurité du legacy) : ce n'est PLUS un « enregistrer » : un identifiant
 * inconnu, ou l'abord d'un autre patient ou d'un autre centre, est refusé (404 {@code ABORD_VASCULAIRE_INTROUVABLE}) ; l'abord n'est
 * jamais créé par un PUT ni réaffecté à un autre patient ou centre. Droit : MEDECIN.
 */
public class ModifierUnAbordVasculaire {
    private final SessionCourante session;
    private final AbordsVasculaires abords;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public ModifierUnAbordVasculaire(SessionCourante session, AbordsVasculaires abords, UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        this.session = session;
        this.abords = abords;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public AbordVasculaire executer(UUID centreDemande, UUID patientDemande, UUID id, String type, String cote, String localisation,
                                    LocalDate creation, LocalDate fin, Boolean actif, String complications) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> {
            AbordVasculaire abord = abords.trouverEnVerrouillant(centre, patient, id).orElseThrow(AbordVasculaireIntrouvable::new);
            abord.remplacer(type, cote, localisation, creation, fin, actif, complications, horloge.maintenant());
            abords.enregistrerLeRemplacement(abord);
            return abord;
        });
    }
}
