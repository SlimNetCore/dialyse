package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;

/**
 * Port sortant : le roulement d'un infirmier (un dépôt par racine d'agrégat). La lecture qui précède une écriture se fait sous le
 * verrou de la fiche de l'infirmier ({@link InfirmiersRepository#pourEcriture}). Les écritures ne portent que sur la ligne touchée
 * par l'intention : une affectation écrite par ailleurs (l'optimisation, P8) n'est jamais réécrite ni supprimée par déduction.
 */
public interface RoulementsRepository {

    /**
     * Le roulement de l'infirmier dans le centre ; sans affectation s'il n'en a aucune.
     */
    RoulementDeLInfirmier de(CentreId centre, InfirmierId infirmier);

    /**
     * Ajoute cette seule affectation.
     */
    void ajouter(CentreId centre, InfirmierId infirmier, Affectation affectation);

    /**
     * Remplace salle, créneau et jours de cette seule affectation.
     */
    void remplacer(CentreId centre, InfirmierId infirmier, Affectation affectation);

    /**
     * Retire cette seule affectation.
     */
    void retirer(CentreId centre, InfirmierId infirmier, AffectationId affectation);
}
