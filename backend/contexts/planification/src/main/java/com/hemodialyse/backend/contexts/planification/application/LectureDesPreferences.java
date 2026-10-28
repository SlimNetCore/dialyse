package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.PreferenceDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ProfilDePlanification;

import java.util.Set;

/**
 * Port sortant (read model) : les écrans de préférences et de profils. Les listes croisent le paramétrage du contexte avec les
 * patients suivis et les infirmiers actifs du centre.
 */
public interface LectureDesPreferences {

    /** Patient suivi (non sorti) avec ses jours prescrits, son créneau actuel et sa préférence (jamais nulle). */
    record LigneDePatient(PatientId patient, String nom, Set<JourDeLaSemaine> jours, CreneauId creneauActuel,
                          PreferenceDePlanification preference) {
    }

    /** Infirmier actif avec son profil (jamais nul). */
    record LigneDInfirmier(InfirmierId infirmier, String nom, String qualification, ProfilDePlanification profil) {
    }

    PageDeResultats<LigneDePatient> patientsSuivis(CentreId centre, Pagination pagination);

    PageDeResultats<LigneDInfirmier> infirmiersActifs(CentreId centre, Pagination pagination);
}
