package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port sortant (PL10b2/PL10c, ADR 0152) : la capacité théorique des centres et le taux d'occupation de leur file active sur une période,
 * calculés par la Planification (méthode SE7f, ADR 0100) à partir des générateurs EN SERVICE installés au plus tard à la fin de la période
 * (GMAO, KB-PLN-76). Lecture seule, sans SQL.
 */
public interface CapaciteDesCentres {

    /** Les paramètres de la méthode de calcul, communs à tous les centres (pour l'expliquer à l'écran). */
    ReglesDeCapacite regles();

    /** Une capacité par centre demandé (jamais vide). */
    Map<UUID, Capacite> parCentre(List<UUID> centres, Periode periode);

    /** La capacité consolidée des centres donnés (liste vide : toutes valeurs à zéro). */
    Capacite consolidee(List<Capacite> centres);

    record ReglesDeCapacite(int generateursParSecours, int seuilProchePourcent) {
    }

    enum Niveau {
        SANS_CAPACITE,
        MARGE,
        PROCHE,
        ATTEINTE
    }

    record Capacite(int generateurs, int generateursSecours, int postesActifs, int series, int patientsParPosteEtSerie, int capacite,
                    long fileActive, BigDecimal tauxOccupation, Niveau niveau) {

        public boolean atteinte() {
            return niveau == Niveau.ATTEINTE;
        }
    }
}
