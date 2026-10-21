package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Langage publié (lecture) : les données de la synthèse de facturation d'un centre (sans mise en page), dont les totaux
 * enregistrés des factures, pour la voie « modèle de document » de la plateforme.
 */
public interface SyntheseDeFacturation {

    SyntheseMensuelle de(UUID centre, LocalDate debut, LocalDate fin);
}
