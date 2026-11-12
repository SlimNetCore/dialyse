package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.application.SeancesDuPatient.TensionsDeSeance;

import java.util.List;
import java.util.Map;

/**
 * Statistiques paramedicales d'un patient sur une periode (modele de lecture) : nombre de seances, moyennes (0.0 sans mesure), poids sec
 * cible en vigueur a la fin de la periode (nul si jamais prescrit), evolution du poids et de la tension.
 */
public record StatistiquesParamedicales(long nombreDeSeances, double moyennePoidsAvant, double moyennePoidsApres,
                                        double moyenneUltrafiltration, Double poidsSecCible,
                                        List<Map<String, Object>> evolutionDuPoids, List<TensionsDeSeance> evolutionDeLaTension) {
}
