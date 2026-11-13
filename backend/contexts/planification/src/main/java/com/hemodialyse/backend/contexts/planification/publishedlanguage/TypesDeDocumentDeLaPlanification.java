package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;

import java.util.List;

/**
 * Les deux types de document que la Planification possède (documents.v1, DOC4c, ADR 0166) : le planning de présence des infirmiers et le
 * planning calendaire d'une proposition d'optimisation. Publiés (et non seulement déclarés) parce que les ACL d'impression de la
 * plateforme les lisent : le code, le libellé et le gabarit livré ne sont écrits qu'ici.
 */
public final class TypesDeDocumentDeLaPlanification {

    private TypesDeDocumentDeLaPlanification() {
    }

    public static final TypeDeDocumentDeclare PLANNING_PRESENCE_INFIRMIERS = new TypeDeDocumentDeclare("PLANNING_PRESENCE_INFIRMIERS",
            "Planning de présence des infirmiers", "reports/planning_presence_infirmiers.jrxml", List.of(), 1,
            "Présence des infirmiers sur une semaine : créneau, salle, absences et remplaçants");

    public static final TypeDeDocumentDeclare PLANNING_OPTIMISE = new TypeDeDocumentDeclare("PLANNING_OPTIMISE",
            "Planning proposé par l'optimisation", "reports/planning_optimise.jrxml", List.of(), 1,
            "Planning calendaire d'une proposition d'optimisation : semaine par semaine, salle et créneau en lignes, jours en colonnes, avec "
                    + "patients, générateurs et infirmiers");

    /** Dans l'ordre où ils sont proposés. */
    public static List<TypeDeDocumentDeclare> tous() {
        return List.of(PLANNING_PRESENCE_INFIRMIERS, PLANNING_OPTIMISE);
    }
}
