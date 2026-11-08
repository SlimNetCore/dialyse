package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Port sortant : les saisies faites pendant la séance (administrations de traitement EPO/fer, volet paramédical, volet médical), tenues par
 * le legacy jusqu'à leur reprise. La suppression d'une séance les efface avec elle, dans la même unité de travail ; effacer ce qui n'existe
 * pas est sans effet.
 */
public interface SaisiesDeLaSeance {
    void effacer(CentreId centre, SeanceId seance);
}
