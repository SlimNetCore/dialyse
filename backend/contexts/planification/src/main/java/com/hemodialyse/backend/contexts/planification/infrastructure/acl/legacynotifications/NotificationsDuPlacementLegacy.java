package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacynotifications;

import com.hemodialyse.backend.contexts.planification.application.NotificationsDuPlacement;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.AlertesDuPlacement;

import java.time.LocalDate;
import java.util.List;

/**
 * ACL vers le centre de notifications (legacy) : traduit les identifiants typés du contexte en identifiants du langage publié
 * {@link AlertesDuPlacement}, fourni par la plateforme. Retirée quand les notifications seront un contexte.
 */
public class NotificationsDuPlacementLegacy implements NotificationsDuPlacement {

    private final AlertesDuPlacement alertes;

    public NotificationsDuPlacementLegacy(AlertesDuPlacement alertes) {
        this.alertes = alertes;
    }

    @Override
    public boolean patientReplaceEnIsolement(CentreId centre, PatientId patient, String nomDuPatient, String salle, LocalDate jour) {
        return alertes.patientReplaceEnIsolement(centre.valeur(), patient.valeur(), nomDuPatient, salle, jour);
    }

    @Override
    public boolean isolementImpossible(CentreId centre, PatientId patient, String nomDuPatient, LocalDate jour) {
        return alertes.isolementImpossible(centre.valeur(), patient.valeur(), nomDuPatient, jour);
    }

    @Override
    public boolean retourEnSalleOrdinairePropose(CentreId centre, PatientId patient, String nomDuPatient, String salleProposee,
                                                 LocalDate jour) {
        return alertes.retourEnSalleOrdinairePropose(centre.valeur(), patient.valeur(), nomDuPatient, salleProposee, jour);
    }

    @Override
    public boolean generateurIndisponible(CentreId centre, GenerateurId generateur, String code, String statut,
                                          int nombreDePatients, List<String> patientsCites, LocalDate jour) {
        return alertes.generateurIndisponible(centre.valeur(), generateur.valeur(), code, statut, nombreDePatients, patientsCites,
                jour);
    }
}
