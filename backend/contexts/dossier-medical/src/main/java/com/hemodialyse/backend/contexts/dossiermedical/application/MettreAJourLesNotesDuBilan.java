package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Remplace les deux textes du bilan (contre-indications, conclusion du néphrologue ; un texte absent est effacé, REPRODUIT, sans limite de
 * longueur). Bilan verrouillé (409 en cas d'écriture simultanée) ; patient inconnu ou d'un autre centre : 404. Droit : MEDECIN.
 */
public class MettreAJourLesNotesDuBilan {
    private final SessionCourante session;
    private final BilansPreGreffe bilans;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;
    private final OuvertureDuBilanPreGreffe ouverture;

    public MettreAJourLesNotesDuBilan(SessionCourante session, PatientsDuCentre patients, BilansPreGreffe bilans, UniteDeTravail uniteDeTravail,
                                      GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.bilans = bilans;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
        this.ouverture = new OuvertureDuBilanPreGreffe(patients, bilans, identifiants, horloge);
    }

    public BilanPreGreffe executer(UUID centreDemande, UUID patientDemande, String contreIndications, String conclusionNephrologue) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> {
            BilanPreGreffe bilan = ouverture.verrouiller(centre, patient);
            bilan.mettreAJourLesNotes(contreIndications, conclusionNephrologue, horloge.maintenant());
            bilans.enregistrer(bilan);
            return bilan;
        });
    }
}
