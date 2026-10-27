package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port publié (lecture) : ce que la Direction demande à la planification pour calculer la capacité théorique d'un centre
 * (RG-PLN-070 à 074) : le nombre de créneaux (séries), les patients par poste et par série, et la file active de la période. Les
 * générateurs viennent de GMAO. Remplace les lectures SQL de {@code position_creneau}, {@code planning_parametres} et {@code patients}
 * (ADR 0053). Bornée au centre ; aucun cache.
 */
public interface BaseDeLaCapaciteTheorique {

    /**
     * @param creneaux              séries par jour
     * @param patientsParPosteEtSerie 3 par défaut, aussi quand la valeur enregistrée est invalide (tolérance du legacy)
     * @param fileActive            patients présents au moins un jour de la période, hors sommeil (RG-PAT-032)
     */
    record Base(int creneaux, int patientsParPosteEtSerie, long fileActive) {
    }

    Base duCentre(UUID centre, LocalDate du, LocalDate au);
}
