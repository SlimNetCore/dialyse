package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;

import java.time.LocalDate;
import java.util.List;

/**
 * Port sortant : les alertes du placement (centre de notifications : journal durable et diffusion). Chaque alerte a une clé
 * déterministe, jour du centre compris : déjà émise, elle n'est ni renvoyée ni dupliquée (livraison au moins une fois des
 * événements, plusieurs instances du serveur). Chaque méthode dit si l'alerte a été émise par cet appel.
 */
public interface NotificationsDuPlacement {

    /** Un patient devenu à risque a été replacé en salle d'isolement (administration, secrétariat, médecin). Chaque replacement est signalé (comme aujourd'hui), le jour est donné pour l'horodatage. */
    boolean patientReplaceEnIsolement(CentreId centre, PatientId patient, String nomDuPatient, String salle, LocalDate jour);

    /** Un patient devenu à risque n'a pas de place d'isolement : à replanifier. Clé : patient et jour (KB-PLN-60). */
    boolean isolementImpossible(CentreId centre, PatientId patient, String nomDuPatient, LocalDate jour);

    /** Un patient redevenu sans risque pourrait quitter la salle d'isolement : retour proposé, jamais appliqué. Clé : patient et jour. */
    boolean retourEnSalleOrdinairePropose(CentreId centre, PatientId patient, String nomDuPatient, String salleProposee,
                                          LocalDate jour);

    /**
     * Un générateur en service est devenu indisponible (administration, secrétariat). Clé : générateur et jour (KB-PLN-63).
     *
     * @param nombreDePatients tous les patients de ce générateur ; {@code patientsCites} n'en nomme que quelques-uns
     */
    boolean generateurIndisponible(CentreId centre, GenerateurId generateur, String code, String statut, int nombreDePatients,
                                   List<String> patientsCites, LocalDate jour);
}
