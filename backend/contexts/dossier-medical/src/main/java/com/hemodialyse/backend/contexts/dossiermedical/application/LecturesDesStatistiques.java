package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.StatistiquesMedicalesPubliees;

import java.time.LocalDate;

/** Les lectures des deux volets de statistiques, sans garde (les gardes sont celles de {@link GardeDesStatistiques}, deja passees). */
final class LecturesDesStatistiques {

    private LecturesDesStatistiques() {
    }

    /** Moyenne absente rendue 0.0 (reproduit) ; poids sec en vigueur a la fin de la periode, sinon au jour du centre ; nul si jamais prescrit. */
    static StatistiquesParamedicales paramedicales(SeancesDuPatient seances, StatistiquesMedicalesPubliees medicales, HorlogeDuCentre horloge,
                                                   GardeDesStatistiques.Demande demande) {
        SeancesDuPatient.MesuresDesSeances mesures = seances.lire(demande.centre(), demande.patient(), demande.periode());
        LocalDate finDeLaPeriode = demande.periode().au() != null ? demande.periode().au() : horloge.aujourdhui(demande.centre());
        Double poidsSec = medicales.poidsSecCibleEnVigueur(demande.centre().valeur(), demande.patient().valeur(), finDeLaPeriode).orElse(null);
        return new StatistiquesParamedicales(mesures.nombre(), zeroSiNul(mesures.moyennePoidsAvant()), zeroSiNul(mesures.moyennePoidsApres()),
                zeroSiNul(mesures.moyenneUltrafiltration()), poidsSec, mesures.evolutionDuPoids(), mesures.evolutionDeLaTension());
    }

    static StatistiquesMedicales medicales(StatistiquesMedicalesPubliees medicales, GardeDesStatistiques.Demande demande) {
        var centre = demande.centre().valeur();
        var patient = demande.patient().valeur();
        var du = demande.periode().du();
        var au = demande.periode().au();
        StatistiquesMedicalesPubliees.Moyennes moyennes = medicales.moyennes(centre, patient, du, au);
        return new StatistiquesMedicales(moyennes.hemoglobine(), moyennes.ktV(), moyennes.ferritine(),
                medicales.tendanceDesBilans(centre, patient, du, au), medicales.tendanceDesPrescriptionsDEpo(centre, patient, du, au));
    }

    private static double zeroSiNul(Double valeur) {
        return valeur != null ? valeur : 0.0d;
    }
}
