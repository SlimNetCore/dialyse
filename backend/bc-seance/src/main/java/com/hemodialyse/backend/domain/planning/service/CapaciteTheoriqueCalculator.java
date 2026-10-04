package com.hemodialyse.backend.domain.planning.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Capacité théorique d'un centre d'hémodialyse (file active maximale de patients chroniques), selon la méthode
 * réglementaire :
 * <ol>
 *   <li>postes actifs = générateurs − générateurs de secours (1 de secours pour 8, arrondi à l'entier supérieur) ;</li>
 *   <li>capacité = postes actifs × séries par jour × patients par poste et par série (3 par défaut, paramétrable par
 *   centre).</li>
 * </ol>
 * Exemple : 16 générateurs, 2 séries par jour → 2 de secours, 14 postes actifs, 14 × 2 × 3 = 84 patients.
 * Classe pure du domaine (aucune dépendance Spring ni JPA).
 */
public final class CapaciteTheoriqueCalculator {

    /**
     * Un générateur de secours est exigé pour huit générateurs.
     */
    public static final int GENERATEURS_PAR_SECOURS = 8;
    /**
     * Patients suivis par poste et par série par défaut (rythme de trois séances par semaine) ; paramétrable par centre.
     */
    public static final int PATIENTS_PAR_POSTE_ET_SERIE_DEFAUT = 3;
    /**
     * À partir de ce taux d'occupation (%), la capacité est jugée proche de la saturation.
     */
    public static final int SEUIL_PROCHE_POURCENT = 90;

    private CapaciteTheoriqueCalculator() {
    }

    public static int generateursDeSecours(int generateurs) {
        if (generateurs <= 0) return 0;
        return (generateurs + GENERATEURS_PAR_SECOURS - 1) / GENERATEURS_PAR_SECOURS;
    }

    /**
     * @param patientsParPosteEtSerie patients suivis par poste et par série (paramètre du centre)
     */
    public static Resultat calculer(int generateurs, int series, int patientsParPosteEtSerie, long fileActive) {
        int g = Math.max(0, generateurs);
        int s = Math.max(0, series);
        int k = Math.max(0, patientsParPosteEtSerie);
        int secours = generateursDeSecours(g);
        int postes = Math.max(0, g - secours);
        return evaluer(g, secours, postes, s, k, postes * s * k, fileActive);
    }

    /**
     * Consolide plusieurs centres : les générateurs, postes, capacités et files actives s'additionnent ; le nombre de
     * séries et de patients par poste n'ont pas de sens consolidé (ils varient d'un centre à l'autre) et valent 0.
     */
    public static Resultat agreger(java.util.List<Resultat> centres) {
        int g = centres.stream().mapToInt(Resultat::generateurs).sum();
        int secours = centres.stream().mapToInt(Resultat::generateursSecours).sum();
        int postes = centres.stream().mapToInt(Resultat::postesActifs).sum();
        int capacite = centres.stream().mapToInt(Resultat::capacite).sum();
        long file = centres.stream().mapToLong(Resultat::fileActive).sum();
        return evaluer(g, secours, postes, 0, 0, capacite, file);
    }

    private static Resultat evaluer(int g, int secours, int postes, int series, int k, int capacite, long fileActive) {
        if (capacite == 0) {
            return new Resultat(g, secours, postes, series, k, 0, fileActive, null, Niveau.SANS_CAPACITE);
        }
        BigDecimal taux = BigDecimal.valueOf(fileActive).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(capacite), 1, RoundingMode.HALF_UP);
        Niveau niveau = fileActive >= capacite ? Niveau.ATTEINTE
                : taux.compareTo(BigDecimal.valueOf(SEUIL_PROCHE_POURCENT)) >= 0 ? Niveau.PROCHE : Niveau.MARGE;
        return new Resultat(g, secours, postes, series, k, capacite, fileActive, taux, niveau);
    }

    public enum Niveau {
        /**
         * Aucune capacité calculable (pas de poste actif ou pas de série).
         */
        SANS_CAPACITE,
        /**
         * Occupation inférieure au seuil de proximité.
         */
        MARGE,
        /**
         * Occupation à {@value #SEUIL_PROCHE_POURCENT} % ou plus, capacité pas encore atteinte.
         */
        PROCHE,
        /**
         * File active égale ou supérieure à la capacité théorique.
         */
        ATTEINTE
    }

    /**
     * @param generateurs        générateurs installés (hors réformés)
     * @param generateursSecours générateurs de secours exigés
     * @param postesActifs       postes de traitement actifs
     * @param series             séries (créneaux) par jour
     * @param capacite           capacité théorique, en patients
     * @param fileActive         patients actuellement suivis
     * @param tauxOccupation     file active / capacité en % (une décimale) ; {@code null} sans capacité
     */
    public record Resultat(int generateurs, int generateursSecours, int postesActifs, int series,
                           int patientsParPosteEtSerie, int capacite, long fileActive, BigDecimal tauxOccupation,
                           Niveau niveau) {
        public boolean atteinte() {
            return niveau == Niveau.ATTEINTE;
        }
    }
}
