package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.CliniquesDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;

import java.util.List;
import java.util.UUID;

/**
 * Port sortant : les indicateurs cliniques (marqueurs face à la cible KDIGO, sérologies, observance, greffes) de chaque centre et leur
 * consolidé sur la période, lus dans le Dossier médical. Tous les centres demandés sont présents (à zéro sans donnée), déjà masqués.
 */
public interface IndicateursCliniquesDesCentres {

    CliniquesDesCentres desCentres(List<UUID> centres, Periode periode);
}
