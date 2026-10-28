package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.PreferenceDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.PreferencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuCentre;

/**
 * Définit la préférence de planification d'un patient (RG-PLN-093). Ordre des refus : valeurs (400), créneau étranger au centre
 * {@code PREFERENCE_CRENEAU_INCONNU}, patient inconnu {@code PREFERENCE_PATIENT_INTROUVABLE}, patient sorti
 * {@code PREFERENCE_PATIENT_SORTI} (KB-PLN-07 corrigé : le legacy l'acceptait puis ne l'affichait plus). Sous le verrou du centre.
 */
public class DefinirLaPreferenceDuPatient {

    private final UniteDeTravail uniteDeTravail;
    private final VerrouDuCentre verrou;
    private final PreferencesRepository preferences;
    private final CreneauxDuCentre creneaux;
    private final PatientsDuCentre patients;

    public DefinirLaPreferenceDuPatient(UniteDeTravail uniteDeTravail, VerrouDuCentre verrou, PreferencesRepository preferences,
                                        CreneauxDuCentre creneaux, PatientsDuCentre patients) {
        this.uniteDeTravail = uniteDeTravail;
        this.verrou = verrou;
        this.preferences = preferences;
        this.creneaux = creneaux;
        this.patients = patients;
    }

    public PreferenceDePlanification executer(CentreId centre, PreferenceDePlanification preference) {
        return uniteDeTravail.executer(() -> {
            verrou.prendre(centre);
            if (preference.creneauPrefere() != null && !creneaux.contient(centre, preference.creneauPrefere())) {
                throw new RegleDePlanificationViolee("PREFERENCE_CRENEAU_INCONNU", "Ce créneau n'appartient pas au centre");
            }
            switch (patients.situation(centre, preference.patient())) {
                case INTROUVABLE -> throw new RegleDePlanificationViolee("PREFERENCE_PATIENT_INTROUVABLE", "Patient introuvable");
                case SORTI -> throw new RegleDePlanificationViolee("PREFERENCE_PATIENT_SORTI",
                        "Ce patient est sorti du centre : sa préférence n'est plus modifiable");
                case EN_SUIVI -> preferences.enregistrer(centre, preference);
            }
            return preference;
        });
    }
}
