package com.hemodialyse.backend.contexts.stock.domain.sortie;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;

import java.util.UUID;

/**
 * Port sortant : ce que le Stock sait d'une séance (contexte Séances, hors Stock) : est-elle facturée (RG-STK-032). Implémenté
 * par une ACL vers la table des séances, en attendant un langage publié de la Facturation.
 */
public interface StatutDesSeances {

    boolean estFacturee(CentreId centre, UUID seanceId);
}

