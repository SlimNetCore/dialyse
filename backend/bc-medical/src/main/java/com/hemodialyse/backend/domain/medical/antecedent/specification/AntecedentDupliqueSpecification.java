package com.hemodialyse.backend.domain.medical.antecedent.specification;

import com.hemodialyse.backend.domain.medical.antecedent.aggregate.Antecedent;
import com.hemodialyse.backend.domain.medical.antecedent.valueobject.StatutClinique;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;

import java.util.List;
import java.util.UUID;

/**
 * Specification (AGENTS.md §14) : un même code CIM-10 ne peut pas être actif deux fois chez
 * le même patient — un antécédent résolu ou inactif n'entre pas en conflit (permet de
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
                .filter(a -> !a.getId().equals(excludingId))
                .filter(a -> a.getStatutClinique() == StatutClinique.ACTIF)
                .noneMatch(a -> diagnostic.equals(a.getDiagnostic()));
    }
}
