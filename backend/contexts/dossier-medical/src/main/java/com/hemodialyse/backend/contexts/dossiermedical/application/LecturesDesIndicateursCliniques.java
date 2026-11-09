package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.CiblesKdigo;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MarqueurSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeCible;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port sortant : les lectures BRUTES par centre dont les indicateurs cliniques de la Direction sont calculés (tables
 * {@code resultats_analyses}, {@code serologies_patient}, {@code alertes_observance}, {@code bilans_pre_greffe}). Un centre sans ligne est
 * absent des cartes. Reproduit du legacy (DM9, ADR 0101) : la dernière valeur de chaque patient sur la période compte autant de fois que
 * de bilans à cette date (KB-MED-109) ; un porteur positif est un patient dont le DERNIER résultat du marqueur, au plus tard à la fin de la
 * période, est {@code POSITIF} (KB-MED-105).
 */
public interface LecturesDesIndicateursCliniques {

    /** Valeurs biologiques suivies par la Direction et leur cible KDIGO. */
    enum Cible {
        KT_V(CiblesKdigo.KT_V), HEMOGLOBINE(CiblesKdigo.HEMOGLOBINE), PHOSPHORE(CiblesKdigo.PHOSPHORE), PTH(CiblesKdigo.PTH),
        ALBUMINE(CiblesKdigo.ALBUMINE);

        private final RegleDeCible regle;

        Cible(RegleDeCible regle) {
            this.regle = regle;
        }

        public RegleDeCible regle() {
            return regle;
        }
    }

    record Greffes(long listeAttente, long bilanEnCours, long greffesPeriode) {
    }

    /** Dernière valeur non nulle de chaque patient sur la période, par centre, dans l'ordre servi. */
    Map<UUID, List<BigDecimal>> dernieresValeurs(Cible cible, List<UUID> centres, LocalDate du, LocalDate au);

    /** Patients distincts dont le dernier résultat du marqueur (au plus tard à {@code au}) est positif, par centre. */
    Map<UUID, Long> porteursPositifs(MarqueurSerologique marqueur, List<UUID> centres, LocalDate au);

    /** Patients distincts ayant une alerte {@code RETARD_CONSTATE} non résolue, par centre. */
    Map<UUID, Long> patientsEnRetardDObservance(List<UUID> centres);

    /** Bilans inscrits en liste d'attente, en cours, et greffes réalisées sur la période, par centre. */
    Map<UUID, Greffes> greffes(List<UUID> centres, LocalDate du, LocalDate au);
}
