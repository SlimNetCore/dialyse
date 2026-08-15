package com.hemodialyse.backend.domain.comptabilite.port;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;

import java.util.List;

/**
 * Port sortant — export des écritures vers un système comptable tiers.
 * Le domaine produit des {@link EcritureComptable} neutres ; les adaptateurs
 * (Sage100ExportAdapter, etc.) savent seuls comment les sérialiser.
 */
public interface ExportComptablePort {

    /**
     * Retourne le contenu du fichier d'export pour les écritures données.
     */
    byte[] exporter(List<EcritureComptable> ecritures);

    /**
     * Identifiant court du format (ex. "SAGE100", "CSV").
     */
    String formatId();
}

