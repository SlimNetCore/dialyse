package com.hemodialyse.backend.contexts.dossiermedical.domain.specification;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ConceptCode;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;

import java.util.List;
import java.util.UUID;

/**
 * Specification (AGENTS.md §14) : un même code CIM-10 ne peut pas être actif deux fois chez le
 * même patient d'un même centre — un antécédent résolu n'entre pas en conflit (permet de
 * ré-ouvrir un diagnostic après guérison puis rechute).
 */
public final class AntecedentDupliqueSpecification {

    private AntecedentDupliqueSpecification() {
    }

    public static boolean estSatisfaitePar(List<Antecedent> existants, ConceptCode diagnostic, UUID excludingId) {
        if (diagnostic == null) {
            return true;
        }
        return existants.stream()
                .filter(a -> !a.getId().value().equals(excludingId))
                .filter(a -> a.getStatutClinique() == StatutClinique.ACTIF)
                .noneMatch(a -> a.getDiagnostic() != null && diagnostic.code().equals(a.getDiagnostic().code()));
    }
}
