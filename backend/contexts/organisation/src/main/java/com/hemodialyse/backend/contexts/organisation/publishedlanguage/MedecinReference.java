package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.UUID;

public record MedecinReference(UUID id, UUID centreId, String nom, String prenom, String specialite) {
}
