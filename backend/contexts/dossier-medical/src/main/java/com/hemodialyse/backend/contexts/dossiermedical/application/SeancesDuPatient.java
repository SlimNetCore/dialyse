package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PeriodeDeStatistiques;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Tension;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Port sortant : les seances d'un patient et les mesures de leur volet parametical (tenues par la Seance, hors contexte), lues pour les
 * statistiques paramedicales. Le comportement du fournisseur est celui du langage publie {@code seance.v1} (KB-SEA-85, 86, 87).
 */
public interface SeancesDuPatient {

    MesuresDesSeances lire(CentreId centre, PatientId patient, PeriodeDeStatistiques periode);

    /**
     * Les mesures de la periode. Moyennes nulles sans mesure. L'evolution du poids garde la forme servie telle quelle par la Seance (une
     * ligne par seance, cles en minuscules) : elle est restituee a l'identique en JSON, la changer changerait le contrat HTTP.
     */
    record MesuresDesSeances(long nombre, Double moyennePoidsAvant, Double moyennePoidsApres, Double moyenneUltrafiltration,
                             List<Map<String, Object>> evolutionDuPoids, List<TensionsDeSeance> evolutionDeLaTension) {
    }

    /** Les tensions avant et apres d'une seance ; la date peut etre nulle comme dans la source. */
    record TensionsDeSeance(LocalDate date, Tension avant, Tension apres) {
    }
}
