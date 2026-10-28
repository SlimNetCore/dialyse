package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Competence;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Port sortant vers Patient et couverture et le Dossier médical : ce que l'optimisation doit savoir d'un patient en plus de sa place
 * (RG-PLN-097) : ses transporteurs (aller et retour) et les compétences qu'il exige des infirmiers, {@code PEDIATRIE} s'il a moins de
 * 18 ans au jour donné, {@code CATHETER} si son abord vasculaire actif en est un à ce jour. Borné au centre ; lecture seule.
 */
public interface ContraintesDesPatients {

    /**
     * Âge en dessous duquel un patient est pédiatrique.
     */
    int AGE_ADULTE = 18;

    /**
     * Les contraintes des patients du centre qui en ont ; les autres en sont absents.
     */
    Map<PatientId, Contraintes> duCentre(CentreId centre, LocalDate aPartirDu);

    record Contraintes(Set<UUID> transporteurs, Set<Competence> competences) {
        public Contraintes {
            transporteurs = transporteurs == null ? Set.of() : Set.copyOf(transporteurs);
            competences = competences == null ? Set.of() : Set.copyOf(competences);
        }

        public static Contraintes aucune() {
            return new Contraintes(Set.of(), Set.of());
        }
    }
}
