package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.Demographie;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.PatientDuMois;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Map;

/**
 * Port sortant (lecture) : les patients tels que le tableau de bord les lit. Les patients appartiennent au Patient et couverture : la
 * lecture passe par une ACL nommée (liste blanche datée, ADR 0095), jamais par le modèle du Patient.
 */
public interface PatientsDuTableauDeBord {

    /** Tous les patients du centre, endormis compris, avec leur programmation hebdomadaire. */
    List<PatientDuMois> duCentre(CentreId centre);

    /**
     * Les patients (sexe et naissance) qui ont une séance réalisée dans le mois du centre, une fois chacun. KB-SEA-65 : la jointure ne
     * filtre pas le centre du patient, un patient d'un autre centre ayant une séance dans ce centre est rendu (reproduit).
     */
    List<Demographie> presentsDuMois(CentreId centre, MoisDuTableauDeBord mois);

    /** Nom et prénom des patients DU centre, par identifiant (détail nominatif, KB-SEA-66). */
    Map<PatientId, Identite> identites(CentreId centre);

    record Identite(String nom, String prenom) {
    }
}
