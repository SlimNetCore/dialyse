package com.hemodialyse.backend.domain.facturation.specification;

import com.hemodialyse.backend.domain.facturation.port.SeanceFacturationCandidate;

import java.util.Set;

public class SeanceEligibleForFacturationSpecification {
    private static final Set<String> ALLOWED_STATUSES = Set.of("VALIDEE", "SIGNEE");

    public boolean isSatisfiedBy(SeanceFacturationCandidate candidate) {
        if (candidate == null || candidate.seanceId() == null || candidate.centerId() == null) {
            return false;
        }
        return candidate.existingFactureId() == null
                && candidate.seanceStatus() != null
                && ALLOWED_STATUSES.contains(candidate.seanceStatus().trim().toUpperCase());
    }
}

