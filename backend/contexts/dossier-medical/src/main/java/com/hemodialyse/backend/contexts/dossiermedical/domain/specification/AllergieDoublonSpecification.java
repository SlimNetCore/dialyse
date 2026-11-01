package com.hemodialyse.backend.contexts.dossiermedical.domain.specification;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;

import java.util.List;
import java.util.UUID;

/**
 * Specification (AGENTS.md §14) : une même allergène active ne peut pas être déclarée deux fois
 * chez le même patient d'un même centre — une allergie résolue n'entre pas en conflit.
 */
public final class AllergieDoublonSpecification {

    private AllergieDoublonSpecification() {
    }

    public static boolean estSatisfaitePar(List<Allergie> existants, String allergene, UUID excludingId) {
        if (allergene == null || allergene.isBlank()) {
            return true;
        }
        String cible = allergene.trim().toLowerCase();
        return existants.stream()
                .filter(a -> !a.getId().value().equals(excludingId))
                .filter(a -> a.getStatutClinique() == StatutClinique.ACTIF)
                .noneMatch(a -> a.getAllergene() != null && a.getAllergene().trim().toLowerCase().equals(cible));
    }
}
