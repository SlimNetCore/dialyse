package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDeSeances;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.SeancesDeLaCaisse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port sortant : les séances RÉALISÉES (validées, signées ou facturées) des centres demandés sur une période, lues dans le langage publié
 * de la Séance (SE7e, ADR 0099). {@code centres} n'est jamais vide.
 */
public interface SeancesRealiseesDesCentres {

    /** Une ligne par centre ayant au moins une séance ; un centre sans séance est absent. */
    Map<UUID, Long> parCentre(List<UUID> centres, Periode periode);

    long auTotal(List<UUID> centres, Periode periode);

    List<MoisDeSeances> parMois(List<UUID> centres, Periode periode);

    /** Par centre et par caisse de la fiche du patient (KB-SEA-83, 84 reproduits par la Séance). */
    List<SeancesDeLaCaisse> parCaisse(List<UUID> centres, Periode periode);
}
