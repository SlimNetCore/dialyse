package com.hemodialyse.backend.contexts.facturation.domain.seance;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant : les séances du centre, tant que la Facturation les modifie (exclusion, réinclusion, surcharge de forfait). Les
 * séances appartiennent au module Séance ; l'adapter est une ACL. Chaque écriture est gardée : elle échoue (422) si la séance
 * n'est plus dans l'état attendu (facturée entre-temps, par exemple).
 */
public interface SeancesDeFacturation {

    Optional<SeanceDeFacturation> trouver(CentreId centre, UUID seance);

    /**
     * Passe la séance, qui a ce statut et n'est pas facturée, à {@code ABSENT} et mémorise ce statut.
     */
    void exclure(CentreId centre, UUID seance, StatutDeSeance statutAvant);

    /**
     * Mémorise l'absence que l'exclusion a déclarée.
     */
    void lierALAbsence(CentreId centre, UUID seance, UUID absence);

    /**
     * Rend à la séance {@code ABSENT} non facturée son statut d'avant l'exclusion et efface la mémoire de l'exclusion.
     */
    void reinclure(CentreId centre, UUID seance, StatutDeSeance statutRetabli);

    /**
     * Pose la surcharge de forfait sur la séance validée ou signée non facturée, avec son auteur et son instant.
     */
    void poserLeForfait(CentreId centre, UUID seance, ForfaitApplique forfait, String auteur, Instant le);
}
