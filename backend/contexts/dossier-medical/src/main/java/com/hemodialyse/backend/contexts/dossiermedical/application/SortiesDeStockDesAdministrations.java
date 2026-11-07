package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Port sortant : la sortie de stock d'une administration faite pendant une séance (tenue par le Stock, hors contexte). */
public interface SortiesDeStockDesAdministrations {

    /** Sort {@code quantite} de l'article du stock du centre, tracée comme une sortie distincte de la séance. Refus : code stable du Stock (422). */
    void sortir(CentreId centre, UUID seance, PatientId patient, LocalDate jour, UUID article, BigDecimal quantite, String auteur);
}
