package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.PatientDeLaSemaine;

import java.util.List;

/**
 * Port sortant vers Patient et couverture : tous les patients du centre avec leur nom (« prénom nom »), leur place actuelle (colonnes
 * de place, catégorie d'isolement déduite des sérologies) et leur séjour (admission, sortie, sommeil). Les règles de présence ne sont
 * pas ici : elles sont dans {@code SejourDuPatient}. Borné au centre ; lecture seule.
 */
public interface SejoursDesPatients {

    List<PatientDeLaSemaine> duCentre(CentreId centre);

    /**
     * Les mêmes patients SANS lire les sérologies : la catégorie d'isolement est alors {@code SANS_RISQUE} par défaut et ne doit pas
     * être lue. Pour les comptages (présence, file active, patients d'un générateur) qui n'ont aucun besoin d'une donnée médicale.
     */
    default List<PatientDeLaSemaine> duCentreSansRisque(CentreId centre) {
        return duCentre(centre);
    }
}
