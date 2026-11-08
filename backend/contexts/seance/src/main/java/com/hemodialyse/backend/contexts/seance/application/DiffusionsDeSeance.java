package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Port sortant : les diffusions temps réel aux écrans du centre (notifications). Appelées APRÈS la validation de la transaction ; un échec
 * de diffusion ne défait jamais la séance. Reproduites à l'identique du legacy (KB-SEA-05).
 */
public interface DiffusionsDeSeance {
    void seanceCreee(CentreId centre, Seance seance, FicheDuPatient patient);

    void seanceValidee(CentreId centre, Seance seance, FicheDuPatient patient);

    /** Saisie de l'infirmier : {@code type} vaut {@code SEANCE_CREEE} ou {@code SEANCE_VALIDEE}. */
    void saisieDeLInfirmier(CentreId centre, String type, Seance seance, FicheDuPatient patient, String auteur);

    /** SE2 : la séance d'un jour passé vient d'être déverrouillée pour régularisation (les infirmiers du centre en sont prévenus). */
    void seanceDeverrouillee(CentreId centre, Seance seance, FicheDuPatient patient);

    /**
     * SE3 : un consommable de la séance a été ajouté, modifié ou retiré (infirmiers et secrétaires du centre). Diffusée à chaque appel,
     * même sans changement de quantité (KB-SEA-28, reproduit : sans risque d'intégrité).
     */
    void consommablesModifies(CentreId centre, Seance seance);
}
