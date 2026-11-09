package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port publié (lecture) : la capacité théorique d'un centre d'hémodialyse et son taux d'occupation sur une période, pour le pilotage de la
 * Direction (RG-PLN-070 à 074 ; SE7f, ADR 0100 : la méthode de calcul quitte le module legacy {@code bc-seance}). Méthode réglementaire :
 * postes actifs = générateurs en service moins un générateur de secours pour {@value #GENERATEURS_PAR_SECOURS} (arrondi à l'entier
 * supérieur) ; capacité = postes actifs x séries par jour x patients par poste et par série (paramétrable par centre, 3 par défaut) ; la file
 * active est celle des patients présents au moins un jour de la période, hors sommeil. Les générateurs en service viennent de la GMAO, lus
 * par l'appelant. Bornée au centre ; aucun cache.
 */
public interface CapaciteTheoriqueDesCentres {

    /** Un générateur de secours est exigé pour huit générateurs. */
    int GENERATEURS_PAR_SECOURS = 8;
    /** À partir de ce taux d'occupation (%), la capacité est jugée proche de la saturation. */
    int SEUIL_PROCHE_POURCENT = 90;

    /** Capacité du centre sur la période, pour le nombre de générateurs en service donné. */
    Capacite duCentre(UUID centre, int generateursEnService, LocalDate du, LocalDate au);

    /**
     * Consolide plusieurs centres : les générateurs, postes, capacités et files actives s'additionnent ; le nombre de séries et de patients
     * par poste n'ont pas de sens consolidé et valent 0.
     */
    Capacite consolidee(List<Capacite> centres);

    enum Niveau {
        /** Aucune capacité calculable (pas de poste actif ou pas de série). */
        SANS_CAPACITE,
        /** Occupation inférieure au seuil de proximité. */
        MARGE,
        /** Occupation au seuil ou plus, capacité pas encore atteinte. */
        PROCHE,
        /** File active égale ou supérieure à la capacité théorique. */
        ATTEINTE
    }

    /**
     * @param generateurs        générateurs en service
     * @param generateursSecours générateurs de secours exigés
     * @param postesActifs       postes de traitement actifs
     * @param series             séries (créneaux) par jour
     * @param capacite           capacité théorique, en patients
     * @param fileActive         patients suivis sur la période
     * @param tauxOccupation     file active / capacité en % (une décimale) ; {@code null} sans capacité
     */
    record Capacite(int generateurs, int generateursSecours, int postesActifs, int series, int patientsParPosteEtSerie, int capacite,
                    long fileActive, BigDecimal tauxOccupation, Niveau niveau) {
        public boolean atteinte() {
            return niveau == Niveau.ATTEINTE;
        }
    }
}
