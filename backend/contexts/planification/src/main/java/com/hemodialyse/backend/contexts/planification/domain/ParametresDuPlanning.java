package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Paramétrage du planning d'un centre (une instance par centre, RG-PLN-001 à 006) : jours d'ouverture (au moins un), salles
 * d'isolement (propriété de la salle, tenue par Organisation), ratio de sécurité et patients par poste et par série. Par
 * défaut : tous les jours, aucune salle d'isolement, ratio 4, 3 patients par poste et par série.
 * <p>
 * {@link #definir} est un <b>remplacement total</b> (KB-PLN-05, décision du 26/10) : un ratio ou un nombre de patients par
 * poste absent revient à sa valeur par défaut ; des salles absentes effacent toutes les salles d'isolement du centre.
 */
public record ParametresDuPlanning(Set<JourDeLaSemaine> joursOuverts, Set<SalleId> sallesIsolement, RatioDeSecurite ratio,
                                   PatientsParPosteEtSerie patientsParPoste) {

    public ParametresDuPlanning {
        if (joursOuverts == null || joursOuverts.isEmpty()) {
            throw new SaisieInvalide("Au moins un jour d'ouverture est requis");
        }
        if (ratio == null) throw new SaisieInvalide("Ratio de sécurité requis");
        if (patientsParPoste == null) throw new SaisieInvalide("Patients par poste et par série requis");
        joursOuverts = Collections.unmodifiableSet(EnumSet.copyOf(joursOuverts));
        sallesIsolement = sallesIsolement == null ? Set.of() : Set.copyOf(sallesIsolement);
    }

    /**
     * Remplace tout le paramétrage. Les valeurs sont contrôlées dans l'ordre jours, ratio, patients par poste (le premier
     * manquement est signalé) ; la salle d'isolement étrangère au centre est contrôlée ensuite par le cas d'usage.
     *
     * @param ratio            absent : valeur par défaut
     * @param patientsParPoste absent : valeur par défaut
     */
    public static ParametresDuPlanning definir(Set<JourDeLaSemaine> jours, Set<SalleId> salles, Integer ratio,
                                               Integer patientsParPoste) {
        if (jours == null || jours.isEmpty()) {
            throw new SaisieInvalide("Au moins un jour d'ouverture est requis");
        }
        RatioDeSecurite ratioRetenu = ratio == null ? RatioDeSecurite.parDefaut() : new RatioDeSecurite(ratio);
        PatientsParPosteEtSerie posteRetenu = patientsParPoste == null ? PatientsParPosteEtSerie.parDefaut()
                : new PatientsParPosteEtSerie(patientsParPoste);
        return new ParametresDuPlanning(jours, salles, ratioRetenu, posteRetenu);
    }

    public static ParametresDuPlanning parDefaut() {
        return new ParametresDuPlanning(EnumSet.allOf(JourDeLaSemaine.class), Set.of(), RatioDeSecurite.parDefaut(),
                PatientsParPosteEtSerie.parDefaut());
    }

    /** Les salles d'isolement sont une propriété de la salle : l'agrégat les reçoit d'Organisation. */
    public ParametresDuPlanning avecLesSallesDIsolement(Set<SalleId> salles) {
        return new ParametresDuPlanning(joursOuverts, salles, ratio, patientsParPoste);
    }
}
