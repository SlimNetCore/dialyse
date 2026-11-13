package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Les affectations et les patients qu'un lot de reprise a créés (identifiants en minuscules). Règle de l'annulation : un assuré repris
 * ne peut plus être supprimé dès qu'une affectation ou un patient que le lot n'a PAS créés s'y rattache (saisi depuis dans la
 * plateforme). Une affectation du lot est ignorée, comme toute affectation d'un patient du lot.
 */
public record DonneesCreeesParLeLot(Set<String> affectations, Set<String> patients) {

    public DonneesCreeesParLeLot {
        affectations = Set.copyOf(affectations);
        patients = Set.copyOf(patients);
    }

    /** Une affectation de l'assuré retient celui-ci si ni elle ni son patient ne viennent du lot. */
    public boolean affectationRetientLAssure(UUID affectation, UUID patient) {
        return !affectations.contains(affectation.toString()) && !patients.contains(patient.toString());
    }

    /** Un patient dont la fiche pointe l'assuré le retient s'il ne vient pas du lot. */
    public boolean patientRetientLAssure(UUID patient) {
        return !patients.contains(patient.toString());
    }
}
