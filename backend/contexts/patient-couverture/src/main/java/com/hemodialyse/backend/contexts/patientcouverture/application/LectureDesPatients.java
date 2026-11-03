package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/** Port sortant de lecture (modèle de lecture, hors agrégat) : la liste des patients d'un centre et les lignes d'effectif. */
public interface LectureDesPatients {

    PageDePatients rechercher(CentreId centre, CriteresDeRecherche criteres);

    /** Tous les patients du centre (ordre non garanti), pour les synthèses mensuelles. */
    List<LigneDEffectif> effectifDuCentre(CentreId centre);
}
