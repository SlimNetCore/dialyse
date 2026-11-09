package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.ChiffresDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ChiffresFinanciers;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDeChiffres;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.ChiffreDeLaCaisse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port sortant : la part financière des centres demandés (factures émises sur la période et leurs règlements de toutes dates, KB-FAC-62
 * reproduit par la Facturation), lue dans son langage publié (F5). {@code centres} n'est jamais vide.
 */
public interface FacturationDesCentres {

    /** Une ligne par centre ayant au moins une facture ; un centre sans facture est absent. */
    Map<UUID, ChiffresDuCentre> parCentre(List<UUID> centres, Periode periode);

    /** Les montants cumulés de tous les centres (zéro sans facture). */
    ChiffresFinanciers cumules(List<UUID> centres, Periode periode);

    List<MoisDeChiffres> parMois(List<UUID> centres, Periode periode);

    /** Le chiffre d'affaires HT par centre et par caisse (code vide sans caisse). */
    List<ChiffreDeLaCaisse> parCaisse(List<UUID> centres, Periode periode);
}
