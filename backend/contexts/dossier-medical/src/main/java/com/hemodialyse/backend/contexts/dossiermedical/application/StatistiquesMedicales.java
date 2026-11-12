package com.hemodialyse.backend.contexts.dossiermedical.application;

import java.util.List;
import java.util.Map;

/**
 * Statistiques medicales d'un patient sur une periode (modele de lecture) : moyennes d'hemoglobine, de Kt/V et de ferritine (0.0 sans
 * donnee), tendance des bilans et tendance des prescriptions d'EPO. Les lignes gardent la forme du langage publie
 * ({@code StatistiquesMedicalesPubliees}, cles en MAJUSCULES du SGBD : KB-MED-103).
 */
public record StatistiquesMedicales(Double hemoglobine, Double ktV, Double ferritine,
                                    List<Map<String, Object>> tendanceDesBilans,
                                    List<Map<String, Object>> tendanceDesPrescriptionsDEpo) {
}
