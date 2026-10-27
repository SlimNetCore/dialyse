package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port consommé par la Planification (ADR 0053, slice P5), fourni par le legacy (centre de notifications : journal durable et diffusion
 * temps réel). Les alertes de l'isolement et du générateur indisponible ont une clé déterministe (type, patient ou générateur, jour du
 * centre) : déjà émise, l'alerte n'est ni renvoyée ni dupliquée, même si l'événement est livré plusieurs fois ou si deux instances du
 * serveur travaillent ensemble (KB-PLN-60, 63). Chaque méthode dit si l'alerte a été émise par cet appel.
 */
public interface AlertesDuPlacement {

    /** Un patient devenu à risque a été replacé en salle d'isolement (administration, secrétariat, médecin). Chaque replacement est signalé. */
    boolean patientReplaceEnIsolement(UUID centre, UUID patient, String nomDuPatient, String salle, LocalDate jour);

    /** Un patient devenu à risque n'a pas de place d'isolement : à replanifier. Clé : patient et jour du centre. */
    boolean isolementImpossible(UUID centre, UUID patient, String nomDuPatient, LocalDate jour);

    /** Un patient redevenu sans risque pourrait quitter la salle d'isolement : retour proposé, jamais appliqué. Clé : patient et jour du centre. */
    boolean retourEnSalleOrdinairePropose(UUID centre, UUID patient, String nomDuPatient, String salleProposee, LocalDate jour);

    /**
     * Un générateur en service est devenu indisponible (administration, secrétariat). Clé : générateur et jour du centre.
     *
     * @param nombreDePatients tous les patients de ce générateur ; {@code patientsCites} n'en nomme que quelques-uns
     */
    boolean generateurIndisponible(UUID centre, UUID generateur, String code, String statut, int nombreDePatients,
                                   List<String> patientsCites, LocalDate jour);
}
