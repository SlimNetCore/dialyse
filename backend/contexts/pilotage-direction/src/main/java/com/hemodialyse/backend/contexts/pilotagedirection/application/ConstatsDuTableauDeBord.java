package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteLevee;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ConstatDesIndicateurs;

import java.util.List;
import java.util.UUID;

/**
 * Port sortant : relit tout ce qu'affiche le tableau de bord d'une société (vue d'ensemble, indicateurs, répartitions, sur la période par
 * défaut) et le range en un constat, avec les alertes actuellement levées. Peut lever une exception : le recalcul la journalise et continue.
 */
public interface ConstatsDuTableauDeBord {

    Lecture lire(UUID societeId);

    record Lecture(ConstatDesIndicateurs constat, List<AlerteLevee> alertes) {
    }
}
