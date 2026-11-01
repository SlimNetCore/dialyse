package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.List;
import java.util.UUID;

public record CentresActifs(List<CentreActif> centres) {
    public record CentreActif(UUID id, String nom) {
    }
}
