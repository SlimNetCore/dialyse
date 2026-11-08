package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/** Port sortant : les volets médicaux (table {@code volet_medical}), un par séance, bornés au centre demandé. */
public interface VoletsMedicaux {

    /** Le volet de la séance du centre ; absent : vide. */
    Optional<VoletMedical> deLaSeance(CentreId centre, SeanceId seance);

    /**
     * Enregistre le volet : l'insère s'il n'existe pas, sinon remplace les six observations et la date de modification (jamais la date
     * de création). À appeler après le verrou de ligne de la séance (deux premières saisies simultanées se suivent).
     */
    void enregistrer(VoletMedical volet);
}
